package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

import java.util.List;

/**
 * /rename 命令 —— 会话重命名。
 * <p>
 * 对应 claude-code 的 /rename 命令，给当前会话设置一个友好名称。
 * 会话名称在 UI 标题栏和会话列表中显示。
 */
public class RenameCommand extends BaseSlashCommand {

    /** 当前会话名称（进程级别） */
    private static volatile String sessionName = null;

    @Override
    public String name() {
        return "rename";
    }

    @Override
    public String description() {
        return "Rename the current session";
    }

    @Override
    public List<String> aliases() {
        return List.of("name", "title");
    }

    @Override
    public String execute(String args, CommandContext context) {
        args = args(args);

        if (args.isBlank()) {
            if (sessionName == null) {
                return AnsiStyle.dim("  No session name set. Use /rename <name> to set one.");
            }
            return "  Current session name: " + AnsiStyle.bold(sessionName);
        }

        if (args.equals("clear") || args.equals("reset")) {
            sessionName = null;
            return AnsiStyle.green("  ✓ Session name cleared");
        }

        // Validate name
        if (args.length() > 100) {
            return AnsiStyle.yellow("  ⚠ Session name too long (max 100 characters)");
        }

        String oldName = sessionName;
        sessionName = args;

        if (oldName != null) {
            return AnsiStyle.green("  ✓ Session renamed: " + oldName + " → " + sessionName);
        }
        return AnsiStyle.green("  ✓ Session named: " + sessionName);
    }

    /** 获取当前会话名称（供其他组件使用） */
    public static String getSessionName() {
        return sessionName;
    }

    /** 设置会话名称（供程序化设置） */
    public static void setSessionName(String name) {
        sessionName = name;
    }
}
