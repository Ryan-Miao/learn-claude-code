package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.SlashCommand;
import com.claudecode.console.AnsiStyle;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * /env 命令 —— 显示环境变量和配置信息。
 */
public class EnvCommand implements SlashCommand {

    @Override
    public String name() { return "env"; }

    @Override
    public String description() { return "Show environment variables and configuration"; }

    @Override
    public String execute(String args, CommandContext context) {
        String trimmed = (args == null) ? "" : args.trim();

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(AnsiStyle.bold("  🔧 Environment\n"));
        sb.append("  ").append("─".repeat(50)).append("\n\n");

        // System info
        sb.append(AnsiStyle.bold("  System\n"));
        sb.append("  OS:       ").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append("\n");
        sb.append("  Java:     ").append(System.getProperty("java.version"))
                .append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("  JVM:      ").append(System.getProperty("java.vm.name")).append("\n");
        sb.append("  Heap:     ").append(formatBytes(Runtime.getRuntime().totalMemory()))
                .append(" / ").append(formatBytes(Runtime.getRuntime().maxMemory())).append("\n");
        sb.append("  PID:      ").append(ProcessHandle.current().pid()).append("\n\n");

        // Work directory
        sb.append(AnsiStyle.bold("  Paths\n"));
        sb.append("  WorkDir:  ").append(System.getProperty("user.dir")).append("\n");
        sb.append("  Home:     ").append(System.getProperty("user.home")).append("\n");
        sb.append("  Config:   ").append(System.getProperty("user.home"))
                .append(File.separator).append(".claude-code-java").append("\n\n");

        // Relevant env vars
        sb.append(AnsiStyle.bold("  Environment Variables\n"));
        List<String> relevantVars = List.of(
                "ANTHROPIC_API_KEY", "OPENAI_API_KEY", "CLAUDE_CODE_",
                "JAVA_HOME", "PATH", "SHELL", "TERM", "EDITOR"
        );

        Map<String, String> env = new TreeMap<>(System.getenv());
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            boolean show = false;
            for (String prefix : relevantVars) {
                if (key.startsWith(prefix) || key.equals(prefix)) {
                    show = true;
                    break;
                }
            }
            if (!show && !trimmed.equals("all")) continue;

            String value = entry.getValue();
            // Mask secrets
            if (key.contains("KEY") || key.contains("SECRET") || key.contains("TOKEN")) {
                value = value.length() > 8 ? value.substring(0, 4) + "****" + value.substring(value.length() - 4) : "****";
            }
            // Truncate long values
            if (value.length() > 80) {
                value = value.substring(0, 77) + "...";
            }
            sb.append("  ").append(AnsiStyle.cyan(key)).append("=").append(value).append("\n");
        }

        if (!trimmed.equals("all")) {
            sb.append("\n").append(AnsiStyle.dim("  Run /env all to show all environment variables"));
        }

        return sb.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1_073_741_824) return String.format("%.1fGB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576) return String.format("%.0fMB", bytes / 1_048_576.0);
        return String.format("%.0fKB", bytes / 1_024.0);
    }
}
