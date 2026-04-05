package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;
import com.claudecode.core.TaskManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * /agent 命令 —— Agent 状态管理。
 * <p>
 * 对应 claude-code 的 agent 管理功能，显示运行中的子 agent/worker 信息。
 * <ul>
 *   <li>/agent —— 列出所有活跃的 agent（任务）</li>
 *   <li>/agent [id] —— 查看特定 agent 详情</li>
 *   <li>/agent stop [id] —— 停止指定 agent</li>
 *   <li>/agent stop-all —— 停止所有 agent</li>
 * </ul>
 */
public class AgentCommand extends BaseSlashCommand {

    @Override
    public String name() {
        return "agent";
    }

    @Override
    public String description() {
        return "View and manage sub-agents/workers";
    }

    @Override
    public List<String> aliases() {
        return List.of("agents", "workers");
    }

    @Override
    public String execute(String args, CommandContext context) {
        args = args(args);

        // Get TaskManager from AgentLoop's tool context
        TaskManager taskManager = getTaskManager(context);
        if (taskManager == null) {
            return AnsiStyle.dim("  No task manager available. Agent management requires an active session.");
        }

        if (args.equals("stop-all")) {
            return handleStopAll(taskManager);
        } else if (args.startsWith("stop ")) {
            return handleStop(args.substring(5).strip(), taskManager);
        } else if (!args.isBlank()) {
            return handleDetail(args, taskManager);
        } else {
            return handleList(taskManager);
        }
    }

    private String handleList(TaskManager taskManager) {
        var allTasks = taskManager.listTasks();
        if (allTasks.isEmpty()) {
            return AnsiStyle.dim("  No agents/workers running.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(AnsiStyle.bold("  🤖 Agents/Workers\n"));
        sb.append("  ").append("─".repeat(60)).append("\n");

        for (var task : allTasks) {
            String statusIcon = switch (task.status()) {
                case RUNNING -> "🟢";
                case PENDING -> "🟡";
                case COMPLETED -> "✅";
                case FAILED -> "❌";
                case CANCELLED -> "⛔";
            };

            String age = formatAge(task.createdAt());

            sb.append("  ").append(statusIcon).append(" ")
                    .append(AnsiStyle.bold(task.id()))
                    .append("  ").append(task.status().name())
                    .append("  ").append(AnsiStyle.dim(age))
                    .append("\n");
            sb.append("    ").append(truncate(task.description(), 60)).append("\n");
        }

        long running = allTasks.stream()
                .filter(t -> t.status() == TaskManager.TaskStatus.RUNNING).count();
        long completed = allTasks.stream()
                .filter(t -> t.status() == TaskManager.TaskStatus.COMPLETED).count();

        sb.append("\n  ").append(AnsiStyle.dim(
                "Total: " + allTasks.size() + " | Running: " + running + " | Completed: " + completed));
        sb.append("\n");

        return sb.toString();
    }

    private String handleDetail(String taskId, TaskManager taskManager) {
        var taskOpt = taskManager.getTask(taskId);
        if (taskOpt.isEmpty()) {
            // Try partial match
            var allTasks = taskManager.listTasks();
            var matched = allTasks.stream()
                    .filter(t -> t.id().startsWith(taskId) || t.description().toLowerCase().contains(taskId.toLowerCase()))
                    .findFirst();
            if (matched.isEmpty()) {
                return AnsiStyle.red("  ✗ No agent found with ID: " + taskId);
            }
            taskOpt = matched;
        }

        var task = taskOpt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(AnsiStyle.bold("  🤖 Agent Detail: " + task.id() + "\n"));
        sb.append("  ").append("─".repeat(50)).append("\n");
        sb.append("  ").append(AnsiStyle.dim("Status:  ")).append(task.status().name()).append("\n");
        sb.append("  ").append(AnsiStyle.dim("Task:    ")).append(task.description()).append("\n");
        sb.append("  ").append(AnsiStyle.dim("Created: ")).append(task.createdAt()).append("\n");
        sb.append("  ").append(AnsiStyle.dim("Updated: ")).append(task.updatedAt()).append("\n");

        if (task.result() != null && !task.result().isBlank()) {
            sb.append("  ").append(AnsiStyle.dim("Result:")).append("\n");
            task.result().lines().limit(20).forEach(line ->
                    sb.append("    ").append(line).append("\n"));
            long totalLines = task.result().lines().count();
            if (totalLines > 20) {
                sb.append("    ").append(AnsiStyle.dim("... (" + (totalLines - 20) + " more lines)")).append("\n");
            }
        }

        if (!task.metadata().isEmpty()) {
            sb.append("  ").append(AnsiStyle.dim("Metadata:")).append("\n");
            task.metadata().forEach((k, v) ->
                    sb.append("    ").append(k).append(": ").append(v).append("\n"));
        }

        return sb.toString();
    }

    private String handleStop(String taskId, TaskManager taskManager) {
        boolean cancelled = taskManager.cancelTask(taskId);
        if (cancelled) {
            return AnsiStyle.green("  ✓ Agent " + taskId + " stopped");
        }

        // Try partial match
        var allTasks = taskManager.listTasks();
        var matched = allTasks.stream()
                .filter(t -> t.id().startsWith(taskId))
                .findFirst();
        if (matched.isPresent()) {
            cancelled = taskManager.cancelTask(matched.get().id());
            if (cancelled) {
                return AnsiStyle.green("  ✓ Agent " + matched.get().id() + " stopped");
            }
            return AnsiStyle.yellow("  ⚠ Agent " + matched.get().id()
                    + " is already " + matched.get().status().name());
        }

        return AnsiStyle.red("  ✗ No running agent found with ID: " + taskId);
    }

    private String handleStopAll(TaskManager taskManager) {
        var running = taskManager.listTasks(TaskManager.TaskStatus.RUNNING);
        if (running.isEmpty()) {
            return AnsiStyle.dim("  No running agents to stop.");
        }

        int stopped = 0;
        for (var task : running) {
            if (taskManager.cancelTask(task.id())) stopped++;
        }

        return AnsiStyle.green("  ✓ Stopped " + stopped + " agent(s)");
    }

    private TaskManager getTaskManager(CommandContext context) {
        // The TaskManager is typically accessible from the tool context
        // via AgentLoop's tool context. Try to access it.
        if (requireAgentLoop(context) == null) return null;
        var toolContext = toolCtx(context);
        if (toolContext == null) return null;
        return toolContext.getOrDefault("TASK_MANAGER", null);
    }

    private String formatAge(Instant created) {
        long seconds = Duration.between(created, Instant.now()).getSeconds();
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3600) return (seconds / 60) + "m ago";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m ago";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }
}
