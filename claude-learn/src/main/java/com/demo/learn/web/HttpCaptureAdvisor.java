package com.demo.learn.web;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interceptor that captures each LLM API round-trip for Spring AI.
 * <p>
 * Method-scoped: each request creates a fresh capturedRounds list.
 * Registered on ChatClient via defaultAdvisors().
 */
public class HttpCaptureAdvisor implements CallAdvisor {

    private final List<ApiRoundTrip> capturedRounds;
    private int roundCounter = 0;

    public HttpCaptureAdvisor(List<ApiRoundTrip> capturedRounds) {
        this.capturedRounds = capturedRounds;
    }

    @Override
    public String getName() {
        return "httpCaptureAdvisor";
    }

    @Override
    public int getOrder() {
        return 100; // runs after tool call logging advisor
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        int round = ++roundCounter;

        // Capture request snapshot
        Prompt prompt = request.prompt();
        String model = extractModel(prompt);
        String systemPrompt = extractSystemPrompt(prompt);
        List<String> requestMessages = prompt.getInstructions().stream()
                .map(msg -> truncate(extractText(msg), 500))
                .collect(Collectors.toList());
        List<String> toolNames = List.of(); // tool names not easily accessible from Prompt

        // Call next advisor in chain
        ChatClientResponse response;
        try {
            response = chain.nextCall(request);
        } catch (Exception e) {
            // On error, log round-trip but re-throw
            capturedRounds.add(new ApiRoundTrip(
                    round,
                    new RequestSnapshot(model, systemPrompt, requestMessages, toolNames),
                    new ResponseSnapshot("", "", e.getMessage() != null ? e.getMessage() : "Error", 0, 0, List.of(), 0)
            ));
            throw e;
        }

        long duration = System.currentTimeMillis() - start;

        // Capture response snapshot
        String responseText = "";
        String thinking = "";
        String finishReason = "UNKNOWN";
        int inputTokens = 0;
        int outputTokens = 0;
        List<ToolCallSnapshot> toolCalls = new ArrayList<>();

        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse != null) {
            // Extract token usage from ChatResponse metadata (response-level, not per-generation)
            try {
                var chatMetadata = chatResponse.getMetadata();
                if (chatMetadata != null) {
                    Usage usage = chatMetadata.getUsage();
                    if (usage != null) {
                        inputTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                        outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                    }
                }
            } catch (Exception ignored) {
                // usage API may vary between Spring AI versions
            }

            for (Generation gen : chatResponse.getResults()) {
                AssistantMessage msg = gen.getOutput();
                // Extract text
                if (msg.getText() != null && !msg.getText().isBlank()) {
                    responseText = msg.getText();
                }

                // Extract thinking from metadata
                Object thinkingMeta = msg.getMetadata().get("thinking");
                if (thinkingMeta instanceof String t) {
                    thinking = t;
                }

                // Extract finish reason from generation metadata
                ChatGenerationMetadata metadata = gen.getMetadata();
                if (metadata != null) {
                    if (metadata.getFinishReason() != null) {
                        finishReason = metadata.getFinishReason();
                    }
                }

                // Extract tool calls from response
                List<AssistantMessage.ToolCall> msgToolCalls = msg.getToolCalls();
                if (msgToolCalls != null) {
                    for (AssistantMessage.ToolCall tc : msgToolCalls) {
                        toolCalls.add(new ToolCallSnapshot(
                                tc.name(),
                                truncate(tc.arguments() != null ? tc.arguments() : "", 2000)
                        ));
                    }
                }
            }
        }

        capturedRounds.add(new ApiRoundTrip(
                round,
                new RequestSnapshot(model, systemPrompt, requestMessages, toolNames),
                new ResponseSnapshot(
                        truncate(responseText, 500),
                        thinking,
                        finishReason,
                        inputTokens,
                        outputTokens,
                        toolCalls,
                        duration
                )
        ));

        return response;
    }

    // --- Helper methods ---

    private String extractModel(Prompt prompt) {
        try {
            if (prompt.getOptions() != null && prompt.getOptions().getModel() != null) {
                return prompt.getOptions().getModel();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String extractSystemPrompt(Prompt prompt) {
        List<Message> instructions = prompt.getInstructions();
        for (Message msg : instructions) {
            if (msg instanceof SystemMessage) {
                return truncate(((SystemMessage) msg).getText(), 300);
            }
        }
        return "";
    }

    private String extractText(Message msg) {
        if (msg instanceof UserMessage userMsg) {
            return maskApiKey(userMsg.getText());
        } else if (msg instanceof AssistantMessage assistMsg) {
            return maskApiKey(assistMsg.getText());
        } else if (msg instanceof SystemMessage sysMsg) {
            return maskApiKey(sysMsg.getText());
        }
        return "";
    }

    private String maskApiKey(String text) {
        if (text == null) return "";
        return text.replaceAll("sk-[a-zA-Z0-9]{20,}", "***REDACTED***");
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "... (truncated)" : text;
    }

    // --- Data records ---

    public record RequestSnapshot(
            String model,
            String systemPrompt,
            List<String> messages,
            List<String> tools
    ) {}

    public record ResponseSnapshot(
            String text,
            String thinking,
            String finishReason,
            int inputTokens,
            int outputTokens,
            List<ToolCallSnapshot> toolCalls,
            long durationMs
    ) {}

    public record ToolCallSnapshot(
            String name,
            String input
    ) {}

    public record ApiRoundTrip(
            int round,
            RequestSnapshot request,
            ResponseSnapshot response
    ) {}
}
