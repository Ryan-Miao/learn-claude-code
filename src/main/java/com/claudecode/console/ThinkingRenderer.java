package com.claudecode.console;

import java.io.PrintStream;

/**
 * Thinking 内容渲染器 —— 对应 claude-code/src/components/Thinking.tsx。
 * <p>
 * 使用 ● 圆点 + &lt;thought&gt; 标签样式显示 AI 的思考过程（参考 Copilot CLI）。
 */
public class ThinkingRenderer {

    private final PrintStream out;

    public ThinkingRenderer(PrintStream out) {
        this.out = out;
    }

    /** 渲染 thinking 内容块（Copilot CLI 的 &lt;thought&gt; 标签风格） */
    public void render(String thinkingContent) {
        if (thinkingContent == null || thinkingContent.isBlank()) {
            return;
        }

        out.println();
        out.println(AnsiStyle.BRIGHT_MAGENTA + "  ● " + AnsiStyle.DIM + "<thought>" + AnsiStyle.RESET);

        // 缩进显示 thinking 内容
        for (String line : thinkingContent.lines().toList()) {
            out.println(AnsiStyle.DIM + "    " + line + AnsiStyle.RESET);
        }

        out.println(AnsiStyle.DIM + "    </thought>" + AnsiStyle.RESET);
        out.println();
    }

    /** 渲染 thinking 开始标记 */
    public void renderStart() {
        out.print(AnsiStyle.BRIGHT_MAGENTA + "  ● " + AnsiStyle.DIM + AnsiStyle.ITALIC
                + "Thinking..." + AnsiStyle.RESET);
    }

    /** 渲染 thinking 结束标记 */
    public void renderEnd() {
        out.println(AnsiStyle.clearLine());
    }
}
