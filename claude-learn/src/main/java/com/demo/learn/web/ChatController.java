package com.demo.learn.web;

import com.demo.learn.core.config.AiConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AiConfig aiConfig;
    private final HttpTrafficCapture trafficCapture;
    private final ChatSession chatSession = new ChatSession();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatController(AiConfig aiConfig, HttpTrafficCapture trafficCapture) {
        this.aiConfig = aiConfig;
        this.trafficCapture = trafficCapture;
        AgentRegistry.setAiConfig(aiConfig);
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

        // Build tool callback lookup (name → callback) for manual execution
        List<ToolCallback> toolCallbacks =
                AgentRegistry.buildToolCallbacks(request.agentId(), capturedCalls);
        Map<String, ToolCallback> toolLookup = new HashMap<>();
        for (ToolCallback cb : toolCallbacks) {
            toolLookup.put(cb.getToolDefinition().name(), cb);
        }

        // Start HTTP traffic capture for this request
        trafficCapture.startCapture();

        try {
            // Build ChatClient WITHOUT advisors — HTTP capture happens at transport layer
            ChatClient chatClient = ChatClient.builder(aiConfig.get())
                    .defaultToolCallbacks(toolCallbacks.toArray(ToolCallback[]::new))
                    .build();

            // Snapshot existing history size for session sync after loop
            List<Message> existingHistory = chatSession.getHistory(request.sessionId());
            int existingSize = existingHistory.size();

            // Local mutable message list for the agent loop
            List<Message> messages = new ArrayList<>(existingHistory);
            int maxIterations = 20;
            ChatResponse finalResponse = null;

            for (int i = 0; i < maxIterations; i++) {
                ChatResponse response = chatClient.prompt()
                        .messages(messages)
                        .call()
                        .chatResponse();

                finalResponse = response;

                if (response.getResults().isEmpty()) break;
                AssistantMessage assistantMsg = response.getResults().get(0).getOutput();

                List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                if (toolCalls == null || toolCalls.isEmpty()) {
                    // No tool calls — final response
                    messages.add(assistantMsg);
                    break;
                }

                // Add assistant message (with tool calls) to conversation
                messages.add(assistantMsg);

                // Execute each tool call and add results
                for (AssistantMessage.ToolCall tc : toolCalls) {
                    try {
                        ToolCallback callback = toolLookup.get(tc.name());
                        String result = (callback != null)
                                ? callback.call(tc.arguments())
                                : "Error: unknown tool " + tc.name();
                        messages.add(ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse(
                                        tc.id(), tc.name(), result)))
                                .build());
                    } catch (Exception e) {
                        // Tool execution error — report as tool result, don't crash
                        messages.add(ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse(
                                        tc.id(), tc.name(), "Tool execution error: " + e.getMessage())))
                                .build());
                    }
                }
            }

            // Extract final text and thinking from last response
            StringBuilder textResponse = new StringBuilder();
            StringBuilder thinkingResponse = new StringBuilder();
            if (finalResponse != null) {
                for (Generation gen : finalResponse.getResults()) {
                    AssistantMessage msg = gen.getOutput();
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
            }

            // Sync new messages back to session for multi-turn correctness
            for (int i = existingSize; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (msg instanceof AssistantMessage am) {
                    chatSession.addAssistantMessage(request.sessionId(), am);
                } else if (msg instanceof ToolResponseMessage trm) {
                    chatSession.addToolResponseMessage(request.sessionId(), trm);
                }
                // UserMessage was already added via addUserMessage above
            }

            // Get captured HTTP traffic
            List<HttpTrafficCapture.CapturedRound> httpRounds = trafficCapture.getRounds();

            return ResponseEntity.ok(Map.of(
                    "text", textResponse.toString(),
                    "thinking", thinkingResponse.toString(),
                    "toolCalls", capturedCalls.stream()
                            .map(c -> Map.<String, Object>of(
                                    "name", c.name(),
                                    "input", c.input(),
                                    "output", c.output()))
                            .toList(),
                    "apiRoundTrips", buildApiRoundTrips(httpRounds),
                    "httpTraffic", buildHttpTrafficFromCapture(httpRounds)
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        } finally {
            trafficCapture.clear();
        }
    }

    /**
     * Parse captured HTTP rounds into structured API round-trip data
     * (model, messages, tokens, tool calls) from actual Anthropic API JSON.
     */
    private List<Map<String, Object>> buildApiRoundTrips(List<HttpTrafficCapture.CapturedRound> rounds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HttpTrafficCapture.CapturedRound round : rounds) {
            try {
                Map<String, Object> reqBody = objectMapper.readValue(
                        round.requestBody(), new TypeReference<>() {});
                Map<String, Object> respBody = objectMapper.readValue(
                        round.responseBody(), new TypeReference<>() {});

                // --- Request data ---
                String model = String.valueOf(reqBody.getOrDefault("model", ""));
                String systemPrompt = "";
                Object system = reqBody.get("system");
                if (system instanceof String s) {
                    systemPrompt = s;
                } else if (system instanceof List<?> list) {
                    // Anthropic format: system can be an array of content blocks
                    systemPrompt = objectMapper.writeValueAsString(list);
                }

                List<String> messages = new ArrayList<>();
                Object msgsObj = reqBody.get("messages");
                if (msgsObj instanceof List<?> msgs) {
                    for (Object msg : msgs) {
                        messages.add(objectMapper.writeValueAsString(msg));
                    }
                }

                List<String> tools = new ArrayList<>();
                Object toolsObj = reqBody.get("tools");
                if (toolsObj instanceof List<?> toolsList) {
                    for (Object tool : toolsList) {
                        if (tool instanceof Map<?, ?> toolMap) {
                            Object name = toolMap.get("name");
                            if (name != null) tools.add(name.toString());
                        }
                    }
                }

                // --- Response data ---
                String text = "";
                String thinking = "";
                Object stopReason = respBody.get("stop_reason");
                String finishReason = stopReason != null ? stopReason.toString() : "unknown";
                int inputTokens = 0;
                int outputTokens = 0;
                List<Map<String, Object>> toolCalls = new ArrayList<>();

                Object usage = respBody.get("usage");
                if (usage instanceof Map<?, ?> usageMap) {
                    Object inTok = usageMap.get("input_tokens");
                    Object outTok = usageMap.get("output_tokens");
                    if (inTok instanceof Number n) inputTokens = n.intValue();
                    if (outTok instanceof Number n) outputTokens = n.intValue();
                }

                Object content = respBody.get("content");
                if (content instanceof List<?> contentList) {
                    for (Object item : contentList) {
                        if (item instanceof Map<?, ?> contentItem) {
                            Object typeObj = contentItem.get("type");
                            String type = typeObj != null ? String.valueOf(typeObj) : "";
                            switch (type) {
                                case "text" -> {
                                    Object t = contentItem.get("text");
                                    text = t != null ? String.valueOf(t) : "";
                                }
                                case "thinking" -> {
                                    Object th = contentItem.get("thinking");
                                    thinking = th != null ? String.valueOf(th) : "";
                                }
                                case "tool_use" -> {
                                    Object n = contentItem.get("name");
                                    Object inp = contentItem.get("input");
                                    toolCalls.add(Map.of(
                                            "name", n != null ? String.valueOf(n) : "",
                                            "input", inp != null ? objectMapper.writeValueAsString(inp) : ""));
                                }
                                default -> {}
                            }
                        }
                    }
                }

                result.add(Map.<String, Object>ofEntries(
                        Map.entry("round", round.round()),
                        Map.entry("request", Map.of(
                                "model", model,
                                "systemPrompt", systemPrompt,
                                "messages", messages,
                                "tools", tools
                        )),
                        Map.entry("response", Map.of(
                                "text", text,
                                "thinking", thinking,
                                "finishReason", finishReason,
                                "inputTokens", inputTokens,
                                "outputTokens", outputTokens,
                                "durationMs", round.durationMs(),
                                "toolCalls", toolCalls
                        ))
                ));
            } catch (Exception e) {
                // Skip rounds that can't be parsed (e.g., non-JSON responses)
            }
        }
        return result;
    }

    /**
     * Convert captured HTTP rounds to the frontend HttpTraffic format.
     * Headers are converted from Map to {name, value, sensitive}[] arrays.
     */
    private List<Map<String, Object>> buildHttpTrafficFromCapture(
            List<HttpTrafficCapture.CapturedRound> rounds) {
        return rounds.stream().map(round -> {
            List<Map<String, Object>> reqHeaders = headersToMapList(round.requestHeaders());
            List<Map<String, Object>> respHeaders = headersToMapList(round.responseHeaders());

            return Map.<String, Object>of(
                    "round", round.round(),
                    "url", round.url(),
                    "method", round.method(),
                    "statusCode", round.statusCode(),
                    "durationMs", round.durationMs(),
                    "requestHeaders", reqHeaders,
                    "requestBody", round.requestBody(),
                    "responseHeaders", respHeaders,
                    "responseBody", round.responseBody()
            );
        }).toList();
    }

    private List<Map<String, Object>> headersToMapList(Map<String, String> headers) {
        return headers.entrySet().stream()
                .map(e -> {
                    String key = e.getKey().toLowerCase();
                    boolean sensitive = key.contains("authorization") || key.contains("api-key")
                            || key.contains("x-api-key") || key.contains("apikey");
                    return Map.<String, Object>of(
                            "name", e.getKey(),
                            "value", e.getValue(),
                            "sensitive", sensitive
                    );
                })
                .toList();
    }

    public record ChatRequest(String message, String agentId, String sessionId) {}
}
