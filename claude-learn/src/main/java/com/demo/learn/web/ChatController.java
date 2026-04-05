package com.demo.learn.web;

import com.demo.learn.core.config.AiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AiConfig aiConfig;
    private final ChatSession chatSession = new ChatSession();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.anthropic.base-url:${spring.ai.openai.base-url:https://api.openai.com}}")
    private String aiBaseUrl;

    @Value("${AI_API_KEY:${spring.ai.anthropic.api-key:${spring.ai.openai.api-key:}}}")
    private String apiKey;

    public ChatController(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    @GetMapping("/agents")
    public List<Map<String, Object>> listAgents() {
        return AgentRegistry.listAgents().stream()
                .map(a -> Map.<String, Object>of(
                        "agentId", a.agentId(),
                        "name", a.name(),
                        "enabled", a.enabled()))
                .toList();
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        AgentRegistry.AgentDef agent = AgentRegistry.getAgent(request.agentId());
        if (agent == null || !agent.enabled()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Unknown or disabled agent: " + request.agentId()));
        }

        // Initialize session with system prompt if new
        chatSession.setSystemPrompt(request.sessionId(), agent.systemPrompt());
        chatSession.addUserMessage(request.sessionId(), request.message());

        // Capture tool calls during this request
        List<CaptureToolCallback.CapturedToolCall> capturedCalls = new ArrayList<>();
        List<ToolCallback> toolCallbacks =
                AgentRegistry.buildToolCallbacks(request.agentId(), capturedCalls);

        // Capture API round-trips during this request
        List<HttpCaptureAdvisor.ApiRoundTrip> capturedRounds = new ArrayList<>();

        try {
            // Build ChatClient with captured tool callbacks and HTTP capture advisor
            ChatClient chatClient = ChatClient.builder(aiConfig.get())
                    .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                    .defaultAdvisors(new HttpCaptureAdvisor(capturedRounds))
                    .build();

            // Get conversation history (system prompt + past messages + current user message)
            List<Message> history = new ArrayList<>(chatSession.getHistory(request.sessionId()));

            // Call with full conversation history
            ChatResponse response = chatClient.prompt()
                    .messages(history)
                    .call()
                    .chatResponse();

            // Extract thinking and text
            StringBuilder textResponse = new StringBuilder();
            StringBuilder thinkingResponse = new StringBuilder();

            for (Generation gen : response.getResults()) {
                AssistantMessage msg = gen.getOutput();
                // Anthropic thinking blocks have specific metadata keys
                if (msg.getMetadata().containsKey("signature")
                        || msg.getMetadata().containsKey("thinking")) {
                    String text = msg.getText();
                    if (text != null && !text.isBlank()) {
                        if (!thinkingResponse.isEmpty()) thinkingResponse.append("\n");
                        thinkingResponse.append(text);
                    }
                } else {
                    String text = msg.getText();
                    if (text != null && !text.isBlank()) {
                        if (!textResponse.isEmpty()) textResponse.append("\n");
                        textResponse.append(text);
                    }
                }
            }

            // Update session history with assistant response
            if (!response.getResults().isEmpty()) {
                chatSession.addAssistantMessage(request.sessionId(),
                        response.getResults().get(0).getOutput());
            }

            return ResponseEntity.ok(Map.of(
                    "text", textResponse.toString(),
                    "thinking", thinkingResponse.toString(),
                    "toolCalls", capturedCalls.stream()
                            .map(c -> Map.<String, Object>of(
                                    "name", c.name(),
                                    "input", c.input(),
                                    "output", c.output()))
                            .toList(),
                    "apiRoundTrips", capturedRounds.stream()
                            .map(rt -> Map.<String, Object>of(
                                    "round", rt.round(),
                                    "request", Map.of(
                                            "model", rt.request().model(),
                                            "systemPrompt", rt.request().systemPrompt(),
                                            "messages", rt.request().messages(),
                                            "tools", rt.request().tools()
                                    ),
                                    "response", Map.of(
                                            "text", rt.response().text(),
                                            "thinking", rt.response().thinking(),
                                            "finishReason", rt.response().finishReason(),
                                            "inputTokens", rt.response().inputTokens(),
                                            "outputTokens", rt.response().outputTokens(),
                                            "durationMs", rt.response().durationMs(),
                                            "toolCalls", rt.response().toolCalls().stream()
                                                    .map(tc -> Map.<String, Object>of("name", tc.name(), "input", tc.input()))
                                                    .toList()
                                    )
                            ))
                            .toList(),
                    "httpTraffic", buildHttpTraffic(capturedRounds)
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    /**
     * Build HTTP traffic data from captured API round-trips.
     * Since Spring AI uses its own HTTP client (OkHttp for Anthropic),
     * we reconstruct the traffic from the advisor-level capture data.
     */
    private List<Map<String, Object>> buildHttpTraffic(List<HttpCaptureAdvisor.ApiRoundTrip> rounds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HttpCaptureAdvisor.ApiRoundTrip rt : rounds) {
            try {
                // Construct synthetic request/response bodies as JSON
                Map<String, Object> reqBody = new LinkedHashMap<>();
                reqBody.put("model", rt.request().model());
                reqBody.put("system", rt.request().systemPrompt());
                reqBody.put("messages", rt.request().messages());
                if (!rt.request().tools().isEmpty()) {
                    reqBody.put("tools", rt.request().tools());
                }

                Map<String, Object> respBody = new LinkedHashMap<>();
                respBody.put("id", "chatcmpl-" + rt.round());
                respBody.put("model", rt.request().model());
                Map<String, Object> respUsage = new LinkedHashMap<>();
                respUsage.put("input_tokens", rt.response().inputTokens());
                respUsage.put("output_tokens", rt.response().outputTokens());
                respBody.put("usage", respUsage);

                List<Map<String, Object>> respChoices = new ArrayList<>();
                Map<String, Object> choice = new LinkedHashMap<>();
                choice.put("index", 0);
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("role", "assistant");
                message.put("content", rt.response().text());
                choice.put("message", message);
                choice.put("finish_reason", rt.response().finishReason());
                respChoices.add(choice);
                respBody.put("choices", respChoices);

                // Build headers with masking
                List<Map<String, Object>> reqHeaders = new ArrayList<>();
                reqHeaders.add(Map.of("name", "content-type", "value", "application/json", "sensitive", false));
                reqHeaders.add(Map.of("name", "authorization", "value", maskApiKey(apiKey), "sensitive", true));
                reqHeaders.add(Map.of("name", "x-api-key", "value", maskApiKey(apiKey), "sensitive", true));

                List<Map<String, Object>> respHeaders = new ArrayList<>();
                respHeaders.add(Map.of("name", "content-type", "value", "application/json", "sensitive", false));
                respHeaders.add(Map.of("name", "x-request-id", "value", "req-" + rt.round(), "sensitive", false));

                // Derive URL from base URL + model
                String url = aiBaseUrl.replaceAll("/+$", "") + "/v1/messages";

                result.add(Map.of(
                        "round", rt.round(),
                        "url", url,
                        "method", "POST",
                        "statusCode", 200,
                        "durationMs", rt.response().durationMs(),
                        "requestHeaders", reqHeaders,
                        "requestBody", objectMapper.writeValueAsString(reqBody),
                        "responseHeaders", respHeaders,
                        "responseBody", objectMapper.writeValueAsString(respBody)
                ));
            } catch (Exception e) {
                // Skip this round if serialization fails
            }
        }
        return result;
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 8) return "***REDACTED***";
        return key.substring(0, 4) + "***REDACTED***";
    }

    public record ChatRequest(String message, String agentId, String sessionId) {}
}
