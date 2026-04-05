package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

/**
 * /theme 命令 —— 切换主题（dark/light/auto）。
 */
public class ThemeCommand extends BaseSlashCommand {

    @Override
    public String name() { return "theme"; }

    @Override
    public String description() { return "Switch color theme (dark/light/auto)"; }

    @Override
    public String execute(String args, CommandContext context) {
        if (requireAgentLoop(context) == null) {
            return noSession();
        }

        var toolCtx = toolCtx(context);
        String current = (String) toolCtx.get("THEME");
        if (current == null) current = "dark";

        String trimmed = args(args).toLowerCase();

        if (trimmed.isEmpty()) {
            return "\n" + AnsiStyle.bold("  🎨 Theme Settings\n")
                    + "  " + "─".repeat(30) + "\n\n"
                    + "  Current: " + AnsiStyle.cyan(current) + "\n"
                    + "  Options: " + AnsiStyle.dim("dark, light, auto") + "\n"
                    + "\n" + AnsiStyle.dim("  Usage: /theme <dark|light|auto>");
        }

        if (!trimmed.equals("dark") && !trimmed.equals("light") && !trimmed.equals("auto")) {
            return AnsiStyle.yellow("  Unknown theme: " + trimmed)
                    + "\n" + AnsiStyle.dim("  Options: dark, light, auto");
        }

        toolCtx.set("THEME", trimmed);
        String icon = switch (trimmed) {
            case "dark" -> "🌙";
            case "light" -> "☀️";
            case "auto" -> "🔄";
            default -> "🎨";
        };
        return AnsiStyle.green("  ✓ Theme set to " + trimmed + " " + icon);
    }
}
