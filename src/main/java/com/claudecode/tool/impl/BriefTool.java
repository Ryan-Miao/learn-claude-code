package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Brief 工具 —— 对应 claude-code 中 brief/detailed 输出模式切换。
 * <p>
 * 控制 Agent 输出的详细程度。开启 brief 模式后，Agent 会：
 * <ul>
 *   <li>使用更简洁的回复</li>
 *   <li>省略冗余的解释</li>
 *   <li>只显示关键信息</li>
 * </ul>
 * <p>
 * 模式状态通过 ToolContext 共享（key: "BRIEF_MODE"）。
 */
public class BriefTool implements Tool {

    @Override
    public String name() {
        return "Brief";
    }

    @Override
    public String description() {
        return """
            Toggle brief output mode. When enabled, responses are concise and focused. \
            When disabled, responses include full explanations and context.
            
            Actions:
            - enable: Turn on brief mode (shorter responses)
            - disable: Turn off brief mode (detailed responses)
            - toggle: Switch between brief and detailed
            - status: Check current mode""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "enum": ["enable", "disable", "toggle", "status"],
                  "description": "Action to perform"
                }
              },
              "required": ["action"]
            }""";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = (String) input.get("action");
        if (action == null) {
            return "Error: 'action' is required";
        }

        boolean currentMode = isBriefMode(context);

        return switch (action) {
            case "enable" -> {
                context.set("BRIEF_MODE", true);
                yield "Brief mode enabled. Responses will be concise.";
            }
            case "disable" -> {
                context.set("BRIEF_MODE", false);
                yield "Brief mode disabled. Responses will be detailed.";
            }
            case "toggle" -> {
                boolean newMode = !currentMode;
                context.set("BRIEF_MODE", newMode);
                yield newMode ? "Brief mode enabled." : "Brief mode disabled.";
            }
            case "status" -> "Brief mode: " + (currentMode ? "ON (concise)" : "OFF (detailed)");
            default -> "Error: Unknown action: " + action;
        };
    }

    /**
     * 检查当前是否为 brief 模式。
     */
    public static boolean isBriefMode(ToolContext context) {
        Object mode = context.get("BRIEF_MODE");
        if (mode instanceof Boolean b) return b;
        return false;
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        String action = (String) input.getOrDefault("action", "toggle");
        return "📋 Brief mode " + action;
    }
}
