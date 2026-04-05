package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.SlashCommand;
import com.claudecode.console.AnsiStyle;

/**
 * /output-style 命令 —— 输出风格设置。
 */
public class OutputStyleCommand implements SlashCommand {

    @Override
    public String name() { return "output-style"; }

    @Override
    public String description() { return "Set output format (markdown/plain/json)"; }

    @Override
    public String execute(String args, CommandContext context) {
        if (context.agentLoop() == null) {
            return AnsiStyle.red("  ✗ No active session");
        }

        var toolCtx = context.agentLoop().getToolContext();
        String current = (String) toolCtx.get("OUTPUT_STYLE");
        if (current == null) current = "markdown";

        String trimmed = (args == null) ? "" : args.trim().toLowerCase();

        if (trimmed.isEmpty()) {
            return "\n" + AnsiStyle.bold("  📝 Output Style\n")
                    + "  " + "─".repeat(30) + "\n\n"
                    + "  Current: " + AnsiStyle.cyan(current) + "\n\n"
                    + "  Options:\n"
                    + "    " + AnsiStyle.bold("markdown") + " — Rich formatting with code blocks\n"
                    + "    " + AnsiStyle.bold("plain") + "    — Plain text, no formatting\n"
                    + "    " + AnsiStyle.bold("json") + "     — JSON structured output\n"
                    + "\n" + AnsiStyle.dim("  Usage: /output-style <markdown|plain|json>");
        }

        if (!trimmed.equals("markdown") && !trimmed.equals("plain") && !trimmed.equals("json")) {
            return AnsiStyle.yellow("  Unknown style: " + trimmed)
                    + "\n" + AnsiStyle.dim("  Options: markdown, plain, json");
        }

        toolCtx.set("OUTPUT_STYLE", trimmed);
        return AnsiStyle.green("  ✓ Output style set to " + trimmed);
    }
}
