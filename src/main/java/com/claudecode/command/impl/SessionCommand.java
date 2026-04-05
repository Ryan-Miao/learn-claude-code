package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.SlashCommand;
import com.claudecode.console.AnsiStyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * /session 命令 —— 会话管理。
 * <p>
 * 显示当前会话信息，支持：
 * <ul>
 *   <li>/session —— 显示当前会话摘要</li>
 *   <li>/session save [name] —— 保存会话快照</li>
 *   <li>/session list —— 列出保存的会话</li>
 *   <li>/session export —— 导出会话为 JSON</li>
 * </ul>
 */
public class SessionCommand implements SlashCommand {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final Instant startTime = Instant.now();

    @Override
    public String name() {
        return "session";
    }

    @Override
    public String description() {
        return "View and manage the current session";
    }

    @Override
    public List<String> aliases() {
        return List.of("sess");
    }

    @Override
    public String execute(String args, CommandContext context) {
        args = args == null ? "" : args.strip();

        if (args.startsWith("save")) {
            String sessionName = args.length() > 5 ? args.substring(5).strip() : "";
            return handleSave(sessionName, context);
        } else if (args.equals("list")) {
            return handleList();
        } else if (args.equals("export")) {
            return handleExport(context);
        } else {
            return showSessionInfo(context);
        }
    }

    private String showSessionInfo(CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(AnsiStyle.bold("  📋 Session Info\n"));
        sb.append("  ").append("─".repeat(50)).append("\n");

        sb.append("  ").append(AnsiStyle.dim("Started: ")).append(FMT.format(startTime)).append("\n");

        // Duration
        long seconds = java.time.Duration.between(startTime, Instant.now()).getSeconds();
        String duration;
        if (seconds < 60) duration = seconds + "s";
        else if (seconds < 3600) duration = (seconds / 60) + "m " + (seconds % 60) + "s";
        else duration = (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        sb.append("  ").append(AnsiStyle.dim("Duration: ")).append(duration).append("\n");

        sb.append("  ").append(AnsiStyle.dim("Work Dir: ")).append(System.getProperty("user.dir")).append("\n");

        // Token usage (from AgentLoop's TokenTracker)
        var agentLoop = context.agentLoop();
        if (agentLoop != null && agentLoop.getAutoCompactManager() != null) {
            sb.append("  ").append(AnsiStyle.dim("Auto-compact: ")).append("enabled").append("\n");
        }

        // Tool count
        int toolCount = context.toolRegistry() != null ? context.toolRegistry().size() : 0;
        sb.append("  ").append(AnsiStyle.dim("Tools: ")).append(toolCount).append(" registered\n");

        // Command count
        int cmdCount = context.commandRegistry() != null ? context.commandRegistry().getCommands().size() : 0;
        sb.append("  ").append(AnsiStyle.dim("Commands: ")).append(cmdCount).append(" available\n");

        // Session memory
        Path memDir = getSessionDir();
        boolean hasMemory = memDir != null && Files.exists(memDir.resolve("SESSION_MEMORY.md"));
        sb.append("  ").append(AnsiStyle.dim("Session Memory: "))
                .append(hasMemory ? "active" : "not yet created").append("\n");

        sb.append("\n  ").append(AnsiStyle.dim("Use /session save [name] to save, /session list to view saved")).append("\n");

        return sb.toString();
    }

    private String handleSave(String sessionName, CommandContext context) {
        Path sessionsDir = getSessionsDir();
        if (sessionsDir == null) {
            return AnsiStyle.red("  ✗ Cannot determine sessions directory");
        }

        try {
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            return AnsiStyle.red("  ✗ Cannot create sessions directory: " + e.getMessage());
        }

        if (sessionName.isBlank()) {
            sessionName = "session-" + FMT.format(Instant.now()).replace(" ", "_").replace(":", "-");
        }

        Path sessionFile = sessionsDir.resolve(sessionName + ".md");
        try {
            StringBuilder content = new StringBuilder();
            content.append("# Session: ").append(sessionName).append("\n\n");
            content.append("- Started: ").append(FMT.format(startTime)).append("\n");
            content.append("- Saved: ").append(FMT.format(Instant.now())).append("\n");
            content.append("- Working Directory: ").append(System.getProperty("user.dir")).append("\n");
            content.append("\n## Notes\n\n");
            content.append("(Add session notes here)\n");

            Files.writeString(sessionFile, content.toString(), StandardCharsets.UTF_8);
            return AnsiStyle.green("  ✓ Session saved: " + sessionFile);
        } catch (IOException e) {
            return AnsiStyle.red("  ✗ Failed to save session: " + e.getMessage());
        }
    }

    private String handleList() {
        Path sessionsDir = getSessionsDir();
        if (sessionsDir == null || !Files.exists(sessionsDir)) {
            return AnsiStyle.dim("  No saved sessions.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(AnsiStyle.bold("  📁 Saved Sessions\n"));
        sb.append("  ").append("─".repeat(50)).append("\n");

        try (var stream = Files.list(sessionsDir)) {
            var files = stream
                    .filter(f -> f.toString().endsWith(".md"))
                    .sorted()
                    .toList();

            if (files.isEmpty()) {
                return AnsiStyle.dim("  No saved sessions.");
            }

            for (var file : files) {
                String name = file.getFileName().toString().replace(".md", "");
                long size = Files.size(file);
                sb.append("  • ").append(name).append(" (").append(size).append(" bytes)\n");
            }
        } catch (IOException e) {
            return AnsiStyle.red("  ✗ Error listing sessions: " + e.getMessage());
        }

        return sb.toString();
    }

    private String handleExport(CommandContext context) {
        Path exportFile = Path.of(System.getProperty("user.dir"), "session-export.json");
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"started\": \"").append(startTime).append("\",\n");
            json.append("  \"exported\": \"").append(Instant.now()).append("\",\n");
            json.append("  \"workDir\": \"")
                    .append(System.getProperty("user.dir").replace("\\", "\\\\")).append("\",\n");
            json.append("  \"tools\": ").append(context.toolRegistry() != null
                    ? context.toolRegistry().size() : 0).append(",\n");
            json.append("  \"commands\": ").append(context.commandRegistry() != null
                    ? context.commandRegistry().getCommands().size() : 0).append("\n");
            json.append("}\n");

            Files.writeString(exportFile, json.toString(), StandardCharsets.UTF_8);
            return AnsiStyle.green("  ✓ Session exported to: " + exportFile);
        } catch (IOException e) {
            return AnsiStyle.red("  ✗ Export failed: " + e.getMessage());
        }
    }

    private Path getSessionsDir() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) return null;
        return Path.of(userHome, ".claude", "sessions");
    }

    private Path getSessionDir() {
        String workDir = System.getProperty("user.dir");
        String sanitized = Path.of(workDir).toAbsolutePath().toString()
                .replace(":", "_").replace("\\", "_").replace("/", "_");
        return Path.of(System.getProperty("user.home"))
                .resolve(".claude").resolve("projects").resolve(sanitized);
    }
}
