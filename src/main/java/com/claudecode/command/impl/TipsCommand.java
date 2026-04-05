package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

import java.util.List;
import java.util.Random;

/**
 * /tips 命令 —— 显示使用技巧和建议。
 */
public class TipsCommand extends BaseSlashCommand {

    private static final List<String> TIPS = List.of(
            "Use /compact when the conversation gets long to reduce token usage",
            "Use /brief to toggle concise output mode for faster responses",
            "Chain commands: 'run tests && fix any failures' lets the agent iterate",
            "Use /plan to enter plan mode — great for complex multi-step tasks",
            "Use /memory to save important context that persists across sessions",
            "Press Ctrl+C to interrupt a long-running tool execution",
            "Use /status to check token usage and session health",
            "Use /commit --push to commit and push in one command",
            "Agent tasks (/tasks) run in parallel — great for concurrent work",
            "Use /vim to enable vi-mode editing in the input field",
            "Use /theme to switch between dark and light themes",
            "Use /diff to review uncommitted changes before committing",
            "CLAUDE.md files in your project root customize agent behavior",
            "Use /skills to discover and load skill templates",
            "Use /plugin search to find community plugins",
            "Use /export to save the conversation as markdown",
            "Use /usage to see detailed token and cost breakdown",
            "Use /doctor to diagnose configuration issues",
            "Use /context to see what files are loaded in context",
            "Use /keybindings to see all keyboard shortcuts"
    );

    private final Random random = new Random();

    @Override
    public String name() { return "tips"; }

    @Override
    public String description() { return "Show usage tips and suggestions"; }

    @Override
    public String execute(String args, CommandContext context) {
        String trimmed = args(args);

        if ("all".equals(trimmed)) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append(AnsiStyle.bold("  💡 All Tips\n"));
            sb.append("  ").append("─".repeat(50)).append("\n\n");
            for (int i = 0; i < TIPS.size(); i++) {
                sb.append(String.format("  %2d. %s%n", i + 1, TIPS.get(i)));
            }
            return sb.toString();
        }

        // Show random tip
        String tip = TIPS.get(random.nextInt(TIPS.size()));
        return "\n  💡 " + AnsiStyle.bold("Tip: ") + tip + "\n\n"
                + AnsiStyle.dim("  Run /tips all to see all tips");
    }
}
