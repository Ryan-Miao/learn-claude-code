package com.demo.learn.web;

import com.demo.learn.core.config.AiConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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
    private final ChatSession chatSession = new ChatSession();

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

        try {
            // Build ChatClient with captured tool callbacks for this request
            ChatClient chatClient = ChatClient.builder(aiConfig.get())
                    .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
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
                            .toList()
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    public record ChatRequest(String message, String agentId, String sessionId) {}
}
