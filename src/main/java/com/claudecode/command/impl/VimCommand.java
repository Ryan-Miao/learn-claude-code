package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.SlashCommand;
import com.claudecode.console.AnsiStyle;

/**
 * /vim 命令 —— 切换 Vi 编辑模式（JLine vi-mode）。
 */
public class VimCommand implements SlashCommand {

    @Override
    public String name() { return "vim"; }

    @Override
    public String description() { return "Toggle vim editing mode"; }

    @Override
    public String execute(String args, CommandContext context) {
        if (context.agentLoop() == null) {
            return AnsiStyle.red("  ✗ No active session");
        }

        var toolCtx = context.agentLoop().getToolContext();
        boolean current = Boolean.TRUE.equals(toolCtx.get("VIM_MODE"));

        String trimmed = (args == null) ? "" : args.trim();
        boolean newMode = switch (trimmed) {
            case "on", "enable" -> true;
            case "off", "disable" -> false;
            default -> !current;
        };

        toolCtx.set("VIM_MODE", newMode);

        if (newMode) {
            return AnsiStyle.green("  ✓ Vim mode ON") + "\n"
                    + AnsiStyle.dim("  Key bindings: ESC → normal mode, i → insert, dd → delete line");
        } else {
            return AnsiStyle.green("  ✓ Vim mode OFF") + " — standard editing";
        }
    }
}
