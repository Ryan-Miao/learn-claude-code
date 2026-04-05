package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件编辑工具 —— 对应 claude-code/src/tools/edit/EditFileTool.ts。
 * <p>
 * 通过精确匹配 old_string 并替换为 new_string 来编辑文件。
 * 编辑后返回 unified diff 格式的变更摘要。
 */
public class FileEditTool implements Tool {

    @Override
    public String name() {
        return "Edit";
    }

    @Override
    public String description() {
        return """
            Make a targeted edit to a file by replacing an exact string match with new content. \
            Performs exact string replacements — the old_string must match exactly one location in the file.

            IMPORTANT RULES:
            - You MUST use the Read tool at least once before editing a file. This tool will error \
            if you haven't read the file first.
            - When editing text from Read tool output, ensure you preserve the exact indentation \
            (tabs/spaces) as it appears in the file.
            - ALWAYS prefer editing existing files in the codebase over creating new files.
            - NEVER write new files unless explicitly required by the task.
            - If old_string matches multiple locations, be more specific by including more context.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "file_path": {
                  "type": "string",
                  "description": "Path to the file to edit"
                },
                "old_string": {
                  "type": "string",
                  "description": "The exact string to find and replace (must be unique)"
                },
                "new_string": {
                  "type": "string",
                  "description": "The replacement string"
                }
              },
              "required": ["file_path", "old_string", "new_string"]
            }""";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String filePath = (String) input.get("file_path");
        String oldString = (String) input.get("old_string");
        String newString = (String) input.get("new_string");

        if (filePath == null || filePath.isBlank()) {
            return "Error: 'file_path' is required.";
        }
        if (oldString == null) {
            return "Error: 'old_string' is required.";
        }
        if (newString == null) {
            return "Error: 'new_string' is required.";
        }

        Path path = context.getWorkDir().resolve(filePath).normalize();

        // Path traversal protection
        if (!path.startsWith(context.getWorkDir().normalize())) {
            return "Error: Path traversal not allowed. Path must be within the working directory.";
        }

        if (!Files.exists(path)) {
            return "Error: File not found: " + path;
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);

            // 检查 old_string 唯一性
            int firstIdx = content.indexOf(oldString);
            if (firstIdx == -1) {
                return "Error: old_string not found in file";
            }

            int secondIdx = content.indexOf(oldString, firstIdx + 1);
            if (secondIdx != -1) {
                return "Error: old_string matches multiple locations. Be more specific.";
            }

            // 执行替换
            String newContent = content.substring(0, firstIdx) + newString + content.substring(firstIdx + oldString.length());
            Files.writeString(path, newContent, StandardCharsets.UTF_8);

            // 生成 unified diff 摘要
            String diff = generateUnifiedDiff(path.toString(), content, newContent);

            // 计算变更统计
            long oldLines = oldString.lines().count();
            long newLines = newString.lines().count();

            StringBuilder result = new StringBuilder();
            result.append("✅ Edited ").append(path)
                    .append(" (replaced ").append(oldLines)
                    .append(" lines with ").append(newLines).append(" lines)\n\n");
            result.append(diff);

            return result.toString().stripTrailing();

        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
        }
    }

    /**
     * 生成 unified diff 格式输出。
     * 类似 TS 版本的 getPatchForEdit()，但使用纯 Java 实现。
     */
    private String generateUnifiedDiff(String filePath, String original, String modified) {
        List<String> oldLines = original.lines().collect(java.util.stream.Collectors.toList());
        List<String> newLines = modified.lines().collect(java.util.stream.Collectors.toList());

        // 找出变更区域
        int contextLines = 3;

        // 找到第一个不同的行
        int firstDiff = 0;
        int minLen = Math.min(oldLines.size(), newLines.size());
        while (firstDiff < minLen && oldLines.get(firstDiff).equals(newLines.get(firstDiff))) {
            firstDiff++;
        }

        // 找到最后一个不同的行（从末尾向前扫描）
        int oldEnd = oldLines.size();
        int newEnd = newLines.size();
        while (oldEnd > firstDiff && newEnd > firstDiff
                && oldLines.get(oldEnd - 1).equals(newLines.get(newEnd - 1))) {
            oldEnd--;
            newEnd--;
        }

        // 计算包含上下文的范围
        int ctxStart = Math.max(0, firstDiff - contextLines);
        int oldCtxEnd = Math.min(oldLines.size(), oldEnd + contextLines);
        int newCtxEnd = Math.min(newLines.size(), newEnd + contextLines);

        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(filePath).append('\n');
        sb.append("+++ b/").append(filePath).append('\n');

        // Hunk header: @@ -oldStart,oldCount +newStart,newCount @@
        int oldHunkSize = oldCtxEnd - ctxStart;
        int newHunkSize = newCtxEnd - ctxStart - (oldEnd - firstDiff) + (newEnd - firstDiff);
        sb.append(String.format("@@ -%d,%d +%d,%d @@%n",
                ctxStart + 1, oldHunkSize, ctxStart + 1, newHunkSize));

        // Context before
        for (int i = ctxStart; i < firstDiff; i++) {
            sb.append(' ').append(oldLines.get(i)).append('\n');
        }
        // Removed lines
        for (int i = firstDiff; i < oldEnd; i++) {
            sb.append('-').append(oldLines.get(i)).append('\n');
        }
        // Added lines
        for (int i = firstDiff; i < newEnd; i++) {
            sb.append('+').append(newLines.get(i)).append('\n');
        }
        // Context after
        for (int i = oldEnd; i < oldCtxEnd; i++) {
            sb.append(' ').append(oldLines.get(i)).append('\n');
        }

        return sb.toString();
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        return "✏️ Editing " + input.getOrDefault("file_path", "file");
    }
}
