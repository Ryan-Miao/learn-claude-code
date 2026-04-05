package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

import java.util.List;

/**
 * /keybindings 命令 —— 显示和配置快捷键。
 */
public class KeybindingsCommand extends BaseSlashCommand {

    @Override
    public String name() { return "keybindings"; }

    @Override
    public String description() { return "Show keyboard shortcuts"; }

    @Override
    public List<String> aliases() {
        return List.of("keys", "shortcuts");
    }

    @Override
    public String execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(AnsiStyle.bold("  ⌨️  Keyboard Shortcuts\n"));
        sb.append("  ").append("─".repeat(50)).append("\n\n");

        sb.append(AnsiStyle.bold("  Input\n"));
        sb.append("  ").append(AnsiStyle.cyan("Enter        ")).append(" Submit message\n");
        sb.append("  ").append(AnsiStyle.cyan("Shift+Enter  ")).append(" New line\n");
        sb.append("  ").append(AnsiStyle.cyan("Tab          ")).append(" Command completion\n");
        sb.append("  ").append(AnsiStyle.cyan("↑/↓          ")).append(" History navigation\n");
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+A       ")).append(" Move to line start\n");
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+E       ")).append(" Move to line end\n");
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+W       ")).append(" Delete word backward\n");
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+U       ")).append(" Delete to line start\n");
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+K       ")).append(" Delete to line end\n\n");

        sb.append(AnsiStyle.bold("  Control\n"));
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+C       ")).append(" Interrupt current operation\n");
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+D       ")).append(" Exit (when input is empty)\n");
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+L       ")).append(" Clear screen\n");
        sb.append("  ").append(AnsiStyle.cyan("Ctrl+R       ")).append(" Reverse history search\n\n");

        sb.append(AnsiStyle.bold("  Vim Mode (/vim to enable)\n"));
        sb.append("  ").append(AnsiStyle.cyan("Esc          ")).append(" Normal mode\n");
        sb.append("  ").append(AnsiStyle.cyan("i            ")).append(" Insert mode\n");
        sb.append("  ").append(AnsiStyle.cyan("a            ")).append(" Append after cursor\n");
        sb.append("  ").append(AnsiStyle.cyan("dd           ")).append(" Delete line\n");
        sb.append("  ").append(AnsiStyle.cyan("yy           ")).append(" Yank line\n");
        sb.append("  ").append(AnsiStyle.cyan("p            ")).append(" Paste\n");
        sb.append("  ").append(AnsiStyle.cyan("w/b          ")).append(" Word forward/backward\n");
        sb.append("  ").append(AnsiStyle.cyan("0/$          ")).append(" Line start/end\n");

        return sb.toString();
    }
}
