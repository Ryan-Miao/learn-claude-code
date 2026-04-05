package com.claudecode.command;

import com.claudecode.core.AgentLoop;
import com.claudecode.tool.ToolContext;

import java.util.List;

/**
 * 命令抽象基类 —— 消除 54 个命令实现中的重复模式。
 * <p>
 * 提供:
 * <ul>
 *   <li>{@link #args(String)} — 安全解析参数</li>
 *   <li>{@link #requireAgentLoop(CommandContext)} — null 检查并返回错误</li>
 *   <li>{@link #toolCtx(CommandContext)} — 快速获取 ToolContext</li>
 *   <li>{@link #success(String)}, {@link #error(String)}, {@link #warning(String)} — 格式化消息</li>
 *   <li>默认 {@link #aliases()} 返回空列表</li>
 * </ul>
 *
 * 子类只需实现 {@link #name()}, {@link #description()}, {@link #execute(String, CommandContext)}。
 * 可选覆盖 {@link #aliases()}。
 */
public abstract class BaseSlashCommand implements SlashCommand {

    @Override
    public List<String> aliases() {
        return List.of();
    }

    // ==================== 参数解析 ====================

    /**
     * 安全解析命令参数（null → 空字符串，去首尾空白）。
     */
    protected String args(String raw) {
        return CommandUtils.parseArgs(raw);
    }

    // ==================== 上下文访问 ====================

    /**
     * 检查 AgentLoop 是否可用。如果为 null，返回错误消息；否则返回 null。
     * 典型用法：
     * <pre>
     *   AgentLoop loop = requireAgentLoop(context);
     *   if (loop == null) return noSession();
     * </pre>
     */
    protected AgentLoop requireAgentLoop(CommandContext context) {
        return context.agentLoop();
    }

    /**
     * 标准 "无会话" 错误消息。
     */
    protected String noSession() {
        return CommandUtils.error("No active session");
    }

    /**
     * 快速获取 ToolContext。如果 agentLoop 为 null 则返回 null。
     */
    protected ToolContext toolCtx(CommandContext context) {
        return context.agentLoop() != null ? context.agentLoop().getToolContext() : null;
    }

    // ==================== 格式化输出 ====================

    /**
     * 成功消息（绿色 ✓）。
     */
    protected String success(String message) {
        return CommandUtils.success(message);
    }

    /**
     * 错误消息（红色 ✗）。
     */
    protected String error(String message) {
        return CommandUtils.error(message);
    }

    /**
     * 警告消息（黄色 ⚠）。
     */
    protected String warning(String message) {
        return CommandUtils.warning(message);
    }

    /**
     * 信息消息（蓝色 ℹ）。
     */
    protected String info(String message) {
        return CommandUtils.info(message);
    }

    /**
     * 格式化标题行。
     */
    protected String header(String emoji, String title) {
        return CommandUtils.header(emoji, title);
    }

    /**
     * 格式化子标题。
     */
    protected String subtitle(String title) {
        return CommandUtils.subtitle(title);
    }
}
