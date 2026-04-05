package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

/**
 * /brief 命令 —— 切换简洁输出模式。
 */
public class BriefCommand extends BaseSlashCommand {

    @Override
    public String name() { return "brief"; }

    @Override
    public String description() { return "Toggle brief output mode"; }

    @Override
    public String execute(String args, CommandContext context) {
        if (requireAgentLoop(context) == null) {
            return noSession();
        }

        var toolCtx = toolCtx(context);
        boolean current = Boolean.TRUE.equals(toolCtx.get("BRIEF_MODE"));

        String trimmed = args(args);
        boolean newMode = switch (trimmed) {
            case "on", "enable", "true" -> true;
            case "off", "disable", "false" -> false;
            default -> !current; // toggle
        };

        toolCtx.set("BRIEF_MODE", newMode);

        return newMode
                ? AnsiStyle.green("  ✓ Brief mode ON") + " — responses will be concise"
                : AnsiStyle.green("  ✓ Brief mode OFF") + " — responses will be detailed";
    }
}
