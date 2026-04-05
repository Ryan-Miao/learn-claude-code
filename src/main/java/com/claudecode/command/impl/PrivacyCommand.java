package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

/**
 * /privacy 命令 —— 隐私设置查看和修改。
 */
public class PrivacyCommand extends BaseSlashCommand {

    @Override
    public String name() { return "privacy"; }

    @Override
    public String description() { return "View/modify privacy settings"; }

    @Override
    public String execute(String args, CommandContext context) {
        if (requireAgentLoop(context) == null) {
            return noSession();
        }

        var toolCtx = toolCtx(context);
        boolean telemetryEnabled = Boolean.TRUE.equals(toolCtx.get("TELEMETRY_ENABLED"));
        boolean sessionLogging = !Boolean.FALSE.equals(toolCtx.get("SESSION_LOGGING"));
        boolean memoryPersist = !Boolean.FALSE.equals(toolCtx.get("MEMORY_PERSIST"));

        String trimmed = args(args).toLowerCase();

        if (trimmed.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append(AnsiStyle.bold("  🔒 Privacy Settings\n"));
            sb.append("  ").append("─".repeat(40)).append("\n\n");
            sb.append("  ").append(statusIcon(telemetryEnabled)).append(" Telemetry:        ")
                    .append(telemetryEnabled ? "Enabled" : "Disabled").append("\n");
            sb.append("  ").append(statusIcon(sessionLogging)).append(" Session Logging:  ")
                    .append(sessionLogging ? "Enabled" : "Disabled").append("\n");
            sb.append("  ").append(statusIcon(memoryPersist)).append(" Memory Persist:   ")
                    .append(memoryPersist ? "Enabled" : "Disabled").append("\n");
            sb.append("\n").append(AnsiStyle.dim("  Toggle: /privacy <telemetry|logging|memory> <on|off>"));
            return sb.toString();
        }

        String[] parts = trimmed.split("\\s+", 2);
        String setting = parts[0];
        boolean enable = parts.length > 1 && ("on".equals(parts[1]) || "enable".equals(parts[1]) || "true".equals(parts[1]));

        return switch (setting) {
            case "telemetry" -> {
                toolCtx.set("TELEMETRY_ENABLED", enable);
                yield AnsiStyle.green("  ✓ Telemetry " + (enable ? "enabled" : "disabled"));
            }
            case "logging" -> {
                toolCtx.set("SESSION_LOGGING", enable);
                yield AnsiStyle.green("  ✓ Session logging " + (enable ? "enabled" : "disabled"));
            }
            case "memory" -> {
                toolCtx.set("MEMORY_PERSIST", enable);
                yield AnsiStyle.green("  ✓ Memory persistence " + (enable ? "enabled" : "disabled"));
            }
            default -> AnsiStyle.yellow("  Unknown setting: " + setting)
                    + "\n" + AnsiStyle.dim("  Options: telemetry, logging, memory");
        };
    }

    private String statusIcon(boolean enabled) {
        return enabled ? AnsiStyle.green("●") : AnsiStyle.red("○");
    }
}
