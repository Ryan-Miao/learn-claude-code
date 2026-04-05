package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * 文件读取工具 —— 对应 claude-code/src/tools/read/ReadFileTool.ts。
 * <p>
 * 读取文件内容，支持行号范围过滤和图片读取（Base64编码）。
 */
public class FileReadTool implements Tool {

    /** 单次读取最大行数 */
    private static final int MAX_LINES = 2000;

    /** 图片最大文件大小 (20MB) */
    private static final long MAX_IMAGE_SIZE = 20 * 1024 * 1024;

    /** 支持的图片扩展名 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico"
    );

    @Override
    public String name() {
        return "Read";
    }

    @Override
    public String description() {
        return """
            Read the contents of a file. Supports text files with optional line ranges, \
            and image files (png, jpg, jpeg, gif, webp, svg, bmp, ico) which are returned \
            as Base64-encoded data with MIME type. For large text files, read in chunks \
            using line_start and line_end.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "file_path": {
                  "type": "string",
                  "description": "Absolute or relative path to the file"
                },
                "line_start": {
                  "type": "integer",
                  "description": "Starting line number (1-based, inclusive). Only for text files."
                },
                "line_end": {
                  "type": "integer",
                  "description": "Ending line number (1-based, inclusive). Only for text files."
                }
              },
              "required": ["file_path"]
            }""";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String filePath = (String) input.get("file_path");
        Path path = context.getWorkDir().resolve(filePath).normalize();

        if (!Files.exists(path)) {
            return "Error: File not found: " + path;
        }
        if (!Files.isRegularFile(path)) {
            return "Error: Not a regular file: " + path;
        }

        // Check if it's an image file
        String ext = getExtension(path.getFileName().toString());
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return readImage(path, ext);
        }

        // Text file reading
        return readText(path, input);
    }

    /**
     * 读取图片文件，返回 Base64 编码数据和 MIME 类型信息。
     */
    private String readImage(Path path, String ext) {
        try {
            long size = Files.size(path);
            if (size > MAX_IMAGE_SIZE) {
                return "Error: Image too large (" + formatSize(size) + "). Max: " + formatSize(MAX_IMAGE_SIZE);
            }
            if (size == 0) {
                return "Error: Image file is empty: " + path;
            }

            byte[] bytes = Files.readAllBytes(path);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mimeType = getMimeType(ext);

            return String.format("""
                {"type": "image", "file_path": "%s", "mime_type": "%s", \
                "size": %d, "base64_length": %d, "base64": "%s"}""",
                    escapeJson(path.toString()), mimeType, size, base64.length(), base64);

        } catch (IOException e) {
            return "Error reading image: " + e.getMessage();
        }
    }

    /**
     * 读取文本文件，支持行范围过滤。
     */
    private String readText(Path path, Map<String, Object> input) {
        try {
            var allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int total = allLines.size();

            int start = 1;
            int end = total;

            if (input.containsKey("line_start")) {
                start = ((Number) input.get("line_start")).intValue();
            }
            if (input.containsKey("line_end")) {
                end = ((Number) input.get("line_end")).intValue();
            }

            start = Math.max(1, start);
            end = Math.min(total, end);

            if (start > end) {
                return "Error: line_start (" + start + ") > line_end (" + end + ")";
            }

            if (end - start + 1 > MAX_LINES) {
                end = start + MAX_LINES - 1;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = start - 1; i < end; i++) {
                sb.append(String.format("%4d | %s%n", i + 1, allLines.get(i)));
            }

            if (end < total) {
                sb.append(String.format("... (%d more lines)%n", total - end));
            }

            return sb.toString().stripTrailing();

        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    /**
     * 获取文件扩展名（小写，不含点）。
     */
    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

    /**
     * 根据扩展名返回 MIME 类型。
     */
    private String getMimeType(String ext) {
        return switch (ext) {
            case "png"  -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif"  -> "image/gif";
            case "webp" -> "image/webp";
            case "svg"  -> "image/svg+xml";
            case "bmp"  -> "image/bmp";
            case "ico"  -> "image/x-icon";
            default     -> "application/octet-stream";
        };
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        String fp = (String) input.getOrDefault("file_path", "file");
        String ext = getExtension(fp);
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return "🖼️ Reading image " + fp;
        }
        return "📄 Reading " + fp;
    }
}
