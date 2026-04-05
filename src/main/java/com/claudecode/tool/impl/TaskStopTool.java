package com.claudecode.tool.impl;

import com.claudecode.core.TaskManager;
import com.claudecode.core.TaskManager.TaskInfo;
import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;

import java.util.Map;
import java.util.Optional;

/**
 * TaskStop 工具 —— 对应 claude-code/src/tools/TaskStopTool。
 * <p>
 * 停止一个正在运行的后台任务。通过 TaskManager.cancelTask() 取消任务执行。
 */
public class TaskStopTool implements Tool {

    private static final String TASK_MANAGER_KEY = "TASK_MANAGER";

    @Override
    public String name() {
        return "TaskStop";
    }

    @Override
    public String description() {
        return """
            Stop a running background task by its ID. Use this tool when you need to terminate \
            a long-running task that is no longer needed, or when a task appears to be stuck. \
            Returns a success or failure status. Tasks in terminal states (COMPLETED/FAILED/CANCELLED) \
            cannot be stopped.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "task_id": {
                  "type": "string",
                  "description": "The ID of the task to stop"
                }
              },
              "required": ["task_id"]
            }""";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String taskId = (String) input.get("task_id");

        if (taskId == null || taskId.isBlank()) {
            return "Error: 'task_id' is required";
        }

        TaskManager taskManager = context.getOrDefault(TASK_MANAGER_KEY, null);
        if (taskManager == null) {
            return "Error: TaskManager is not available";
        }

        // Check if task exists first
        Optional<TaskInfo> taskOpt = taskManager.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return "Error: Task not found: " + taskId;
        }

        TaskInfo task = taskOpt.get();
        String previousStatus = task.status().name();

        boolean cancelled = taskManager.cancelTask(taskId);
        if (cancelled) {
            return String.format("""
                {"status": "stopped", "task_id": "%s", "previous_status": "%s", \
                "description": "%s"}""",
                    taskId, previousStatus, escapeJson(task.description()));
        } else {
            return String.format(
                    "Error: Cannot stop task %s — it is already in terminal state: %s",
                    taskId, previousStatus);
        }
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        return "🛑 Stopping task " + input.getOrDefault("task_id", "...");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
