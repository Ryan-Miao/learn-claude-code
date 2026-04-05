package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

/**
 * /vim 命令 —— 切换 Vi 编辑模式（JLine vi-mode）。
 */
public class VimCommand extends BaseSlashCommand {

    @Override
    public String name() { return "vim"; }

    @Override
    public String description() { return "Toggle vim editing mode"; }

    @Override
    public String execute(String args, CommandContext context) {
        if (requireAgentLoop(context) == null) {
            return noSession();
        }

        var toolCtx = toolCtx(context);
        boolean current = Boolean.TRUE.equals(toolCtx.get("VIM_MODE"));

        String trimmed = args(args);
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
