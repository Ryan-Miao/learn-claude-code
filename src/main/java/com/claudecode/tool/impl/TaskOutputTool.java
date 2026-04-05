package com.claudecode.tool.impl;

import com.claudecode.core.TaskManager;
import com.claudecode.core.TaskManager.TaskInfo;
import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;

import java.util.Map;
import java.util.Optional;

/**
 * TaskOutput 工具 —— 对应 claude-code/src/tools/TaskOutputTool。
 * <p>
 * 获取任务的执行输出/结果。当任务完成后，可以通过此工具读取其结果。
 * 对于正在运行的任务，返回当前状态信息。
 */
public class TaskOutputTool implements Tool {

    private static final String TASK_MANAGER_KEY = "TASK_MANAGER";

    @Override
    public String name() {
        return "TaskOutput";
    }

    @Override
    public String description() {
        return """
            Get the output/result of a task. Use this to retrieve the result of a completed task, \
            or to check the current status of a running task. For completed tasks, returns the full \
            execution result. For running tasks, returns the current status.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "task_id": {
                  "type": "string",
                  "description": "The ID of the task to get output from"
                }
              },
              "required": ["task_id"]
            }""";
    }

    @Override
    public boolean isReadOnly() {
        return true;
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

        Optional<TaskInfo> taskOpt = taskManager.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return "Error: Task not found: " + taskId;
        }

        TaskInfo task = taskOpt.get();

        return switch (task.status()) {
            case COMPLETED -> {
                String result = task.result();
                yield String.format("""
                    {"task_id": "%s", "status": "COMPLETED", "description": "%s", "result": "%s"}""",
                        taskId, escapeJson(task.description()),
                        escapeJson(result != null ? result : "(no output)"));
            }
            case FAILED -> {
                String error = task.result();
                yield String.format("""
                    {"task_id": "%s", "status": "FAILED", "description": "%s", "error": "%s"}""",
                        taskId, escapeJson(task.description()),
                        escapeJson(error != null ? error : "(unknown error)"));
            }
            case CANCELLED -> String.format("""
                {"task_id": "%s", "status": "CANCELLED", "description": "%s"}""",
                    taskId, escapeJson(task.description()));
            case RUNNING -> String.format("""
                {"task_id": "%s", "status": "RUNNING", "description": "%s", \
                "message": "Task is still running. Check back later."}""",
                    taskId, escapeJson(task.description()));
            case PENDING -> String.format("""
                {"task_id": "%s", "status": "PENDING", "description": "%s", \
                "message": "Task has not started yet."}""",
                    taskId, escapeJson(task.description()));
        };
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        return "📋 Getting output of task " + input.getOrDefault("task_id", "...");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
