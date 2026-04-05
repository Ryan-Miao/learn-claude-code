package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;

import java.util.Map;

/**
 * Sleep 工具 —— 对应 claude-code/src/tools/SleepTool。
 * <p>
 * 等待指定时长。用于暂停操作、等待外部进程、或用户要求休眠。
 * 支持通过中断取消等待。
 */
public class SleepTool implements Tool {

    private static final long MAX_DURATION_MS = 300_000; // 5 minutes max

    @Override
    public String name() {
        return "Sleep";
    }

    @Override
    public String description() {
        return """
            Wait for a specified duration in milliseconds. The user can interrupt the sleep at \
            any time. Use this when:
            - The user tells you to sleep or rest
            - You have nothing to do and are waiting for something
            - You need to wait for an external process to complete
            Maximum duration: 300000ms (5 minutes).""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "duration_ms": {
                  "type": "integer",
                  "description": "Duration to sleep in milliseconds (max: 300000)"
                }
              },
              "required": ["duration_ms"]
            }""";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        Number durationNum = (Number) input.get("duration_ms");
        if (durationNum == null) {
            return "Error: 'duration_ms' is required";
        }

        long durationMs = durationNum.longValue();
        if (durationMs <= 0) {
            return "Error: duration_ms must be positive";
        }
        if (durationMs > MAX_DURATION_MS) {
            durationMs = MAX_DURATION_MS;
        }

        long startTime = System.currentTimeMillis();
        try {
            Thread.sleep(durationMs);
            long elapsed = System.currentTimeMillis() - startTime;
            return String.format("Slept for %d ms", elapsed);
        } catch (InterruptedException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            Thread.currentThread().interrupt();
            return String.format("Sleep interrupted after %d ms (requested %d ms)", elapsed, durationMs);
        }
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        Number ms = (Number) input.getOrDefault("duration_ms", 0);
        return "💤 Sleeping " + (ms.longValue() / 1000.0) + "s";
    }
}
