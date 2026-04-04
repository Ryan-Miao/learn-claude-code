package com.demo.learn.web;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

/**
 * Decorates a ToolCallback to capture input/output for web response.
 */
public class CaptureToolCallback implements ToolCallback {

    private static final int MAX_OUTPUT_LENGTH = 2000;

    private final ToolCallback delegate;
    private final List<CapturedToolCall> capturedCalls;

    public CaptureToolCallback(ToolCallback delegate, List<CapturedToolCall> capturedCalls) {
        this.delegate = delegate;
        this.capturedCalls = capturedCalls;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String result = delegate.call(toolInput);
        capturedCalls.add(new CapturedToolCall(
                getToolDefinition().name(),
                truncate(toolInput, MAX_OUTPUT_LENGTH),
                truncate(result, MAX_OUTPUT_LENGTH)
        ));
        return result;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        capturedCalls.add(new CapturedToolCall(
                getToolDefinition().name(),
                truncate(toolInput, MAX_OUTPUT_LENGTH),
                truncate(result, MAX_OUTPUT_LENGTH)
        ));
        return result;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "... (truncated)" : text;
    }

    /**
     * Record of a single tool call for the web response.
     */
    public record CapturedToolCall(String name, String input, String output) {}
}
