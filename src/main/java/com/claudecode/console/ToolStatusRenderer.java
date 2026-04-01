package com.claudecode.console;

import java.io.PrintStream;

/**
 * 工具调用状态渲染器 —— 对应 claude-code/src/components/ToolStatus.tsx。
 * <p>
 * 使用彩色 ● 圆点标识工具调用状态，配合 ⎿ 显示结果（参考 Claude Code 样式）。
 */
public class ToolStatusRenderer {

    private final PrintStream out;

    public ToolStatusRenderer(PrintStream out) {
        this.out = out;
    }

    /** 渲染工具调用开始 */
    public void renderStart(String toolName, String args) {
        out.println();
        out.print(AnsiStyle.BRIGHT_BLUE + "  ● " + AnsiStyle.BOLD + toolName + AnsiStyle.RESET);

        // 提取并显示简短参数
        if (args != null && !args.isBlank()) {
            String summary = extractSummary(toolName, args);
            if (summary != null) {
                out.print(AnsiStyle.dim("(" + summary + ")"));
            }
        }
        out.println(AnsiStyle.dim("  running..."));
    }

    /** 渲染工具调用完成 */
    public void renderEnd(String toolName, String result) {
        // 截断长结果
        String display = result;
        if (display != null && display.length() > 500) {
            display = display.substring(0, 497) + "...";
        }

        out.println(AnsiStyle.GREEN + "  ● " + AnsiStyle.BOLD + toolName + AnsiStyle.RESET
                + AnsiStyle.dim("  done"));

        if (display != null && !display.isBlank()) {
            // 使用 ⎿ 前缀显示结果（Claude Code 风格）
            String[] lines = display.lines().toArray(String[]::new);
            for (int i = 0; i < lines.length; i++) {
                if (i == 0) {
                    out.println(AnsiStyle.DIM + "  ⎿  " + lines[i] + AnsiStyle.RESET);
                } else {
                    out.println(AnsiStyle.DIM + "     " + lines[i] + AnsiStyle.RESET);
                }
            }
        }
    }

    /** 渲染工具错误 */
    public void renderError(String toolName, String error) {
        out.println(AnsiStyle.RED + "  ● " + AnsiStyle.BOLD + toolName + AnsiStyle.RESET
                + AnsiStyle.red("  error"));
        if (error != null) {
            out.println(AnsiStyle.DIM + "  ⎿  " + AnsiStyle.RED + error + AnsiStyle.RESET);
        }
    }

    /** 从 JSON 参数中提取人类可读的摘要 */
    private String extractSummary(String toolName, String args) {
        try {
            if (args.contains("\"command\"")) {
                int start = args.indexOf("\"command\"");
                int valStart = args.indexOf("\"", start + 10) + 1;
                int valEnd = args.indexOf("\"", valStart);
                if (valStart > 0 && valEnd > valStart) {
                    String cmd = args.substring(valStart, Math.min(valEnd, valStart + 60));
                    return "$ " + cmd;
                }
            }
            if (args.contains("\"file_path\"")) {
                int start = args.indexOf("\"file_path\"");
                int valStart = args.indexOf("\"", start + 12) + 1;
                int valEnd = args.indexOf("\"", valStart);
                if (valStart > 0 && valEnd > valStart) {
                    return args.substring(valStart, valEnd);
                }
            }
            if (args.contains("\"pattern\"")) {
                int start = args.indexOf("\"pattern\"");
                int valStart = args.indexOf("\"", start + 10) + 1;
                int valEnd = args.indexOf("\"", valStart);
                if (valStart > 0 && valEnd > valStart) {
                    return "pattern: " + args.substring(valStart, valEnd);
                }
            }
            if (args.contains("\"query\"")) {
                int start = args.indexOf("\"query\"");
                int valStart = args.indexOf("\"", start + 8) + 1;
                int valEnd = args.indexOf("\"", valStart);
                if (valStart > 0 && valEnd > valStart) {
                    return "\"" + args.substring(valStart, Math.min(valEnd, valStart + 60)) + "\"";
                }
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return null;
    }
}
