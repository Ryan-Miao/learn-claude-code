package com.claudecode.console;

import java.io.PrintStream;

/**
 * Banner 打印器 —— 对应 claude-code/src/components/Banner.tsx。
 * <p>
 * 在启动时打印带边框的 Logo 和版本信息。
 * 参考 Copilot CLI / Claude Code 的边框样式。
 */
public class BannerPrinter {

    private static final String VERSION = "0.1.0-SNAPSHOT";

    // 边框字符
    private static final String TL = "╭", TR = "╮", BL = "╰", BR = "╯";
    private static final String H = "─", V = "│";

    /**
     * 打印带边框的启动 Banner。
     *
     * @param out      输出流
     * @param provider API 提供者名称
     * @param model    模型名称
     * @param baseUrl  API URL
     * @param workDir  工作目录
     * @param toolCount 工具数量
     * @param cmdCount  命令数量
     * @param termInfo  终端信息
     */
    public static void printBoxed(PrintStream out, String provider, String model,
                                   String baseUrl, String workDir,
                                   int toolCount, int cmdCount, String termInfo) {
        int boxWidth = 90;
        String hr = H.repeat(boxWidth - 2);

        // Logo（ASCII 冒烟咖啡杯 — 每行精确 16 字符宽）
        String[] logo = {
                "      ) ) )     ",
                "   ╭────────╮   ",
                "   │ ~~~~~~ │   ",
                "   │ CLAUDE │   ",
                "   │  CODE  │   ",
                "   ╰─┬────┬─╯   "
        };

        // 右侧信息
        String titleLine = "Claude Code Java  v" + VERSION;
        String descLine = "Describe a task to get started.";
        String tipLine = "Tip: /help for commands, Tab to complete";

        // 打印顶部边框
        out.println();
        out.println("  " + TL + "─── " + AnsiStyle.BOLD + AnsiStyle.BRIGHT_CYAN
                + "Claude Code Java" + AnsiStyle.RESET + AnsiStyle.DIM + " v" + VERSION
                + AnsiStyle.RESET + " " + H.repeat(boxWidth - 28 - VERSION.length()) + TR);

        // Logo + 右侧信息（双列布局）
        int logoWidth = 16;    // logo 视觉宽度
        int rightStart = logoWidth + 4;
        int contentWidth = boxWidth - 4; // 边框内可用宽度

        String[] rightInfo = {
                "",
                AnsiStyle.BOLD + "Welcome!" + AnsiStyle.RESET,
                "",
                AnsiStyle.DIM + "Provider: " + AnsiStyle.RESET + AnsiStyle.CYAN + provider.toUpperCase() + AnsiStyle.RESET
                        + AnsiStyle.DIM + "  Model: " + AnsiStyle.RESET + AnsiStyle.CYAN + model + AnsiStyle.RESET,
                AnsiStyle.DIM + "Work Dir: " + workDir + AnsiStyle.RESET,
                AnsiStyle.DIM + "Tools: " + toolCount + " | Commands: " + cmdCount + " | " + termInfo + AnsiStyle.RESET,
        };

        int maxRows = Math.max(logo.length, rightInfo.length);
        for (int i = 0; i < maxRows; i++) {
            String leftPart = i < logo.length ? logo[i] : "";
            String rightPart = i < rightInfo.length ? rightInfo[i] : "";

            // 左侧 logo 部分（固定宽度，无 ANSI 所以直接 pad）
            String paddedLeft = padRight(leftPart, logoWidth);

            // 输出行
            out.println("  " + V + "  "
                    + AnsiStyle.BRIGHT_CYAN + paddedLeft + AnsiStyle.RESET
                    + AnsiStyle.DIM + " │ " + AnsiStyle.RESET
                    + rightPart);
        }

        // 底部边框
        out.println("  " + BL + hr + BR);
    }

    /**
     * 精简版 banner（用于窄终端或 Scanner 模式）。
     */
    public static void printCompact(PrintStream out) {
        out.println();
        out.println(AnsiStyle.BRIGHT_CYAN + AnsiStyle.BOLD + "  ◆ Claude Code (Java)" + AnsiStyle.RESET
                + AnsiStyle.dim("  v" + VERSION));
        out.println(AnsiStyle.dim("  Type /help for commands  •  Ctrl+D to exit"));
        out.println();
    }

    /** 右侧补空格到指定视觉宽度 */
    private static String padRight(String s, int width) {
        int len = s.length();
        if (len >= width) return s;
        return s + " ".repeat(width - len);
    }

    /** 获取版本号 */
    public static String getVersion() {
        return VERSION;
    }
}
