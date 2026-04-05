package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.SlashCommand;
import com.claudecode.console.AnsiStyle;
import com.claudecode.core.TokenTracker;

/**
 * /usage 命令 —— 详细 token 和费用统计。
 */
public class UsageCommand implements SlashCommand {

    @Override
    public String name() { return "usage"; }

    @Override
    public String description() { return "Show detailed token usage and cost breakdown"; }

    @Override
    public String execute(String args, CommandContext context) {
        if (context.agentLoop() == null) {
            return AnsiStyle.red("  ✗ No active session");
        }

        TokenTracker tracker = context.agentLoop().getTokenTracker();

        long inputTokens = tracker.getInputTokens();
        long outputTokens = tracker.getOutputTokens();
        long totalTokens = inputTokens + outputTokens;

        // Approximate costs (Claude 3.5 Sonnet pricing)
        double inputCost = inputTokens / 1_000_000.0 * 3.0;  // $3/M input
        double outputCost = outputTokens / 1_000_000.0 * 15.0; // $15/M output
        double totalCost = inputCost + outputCost;

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(AnsiStyle.bold("  📊 Token Usage & Cost\n"));
        sb.append("  ").append("─".repeat(40)).append("\n\n");

        sb.append("  ").append(AnsiStyle.bold("Model:          ")).append(AnsiStyle.cyan(tracker.getModelName())).append("\n\n");

        sb.append("  ").append(AnsiStyle.bold("Input Tokens:   ")).append(formatNumber(inputTokens)).append("\n");
        sb.append("  ").append(AnsiStyle.bold("Output Tokens:  ")).append(formatNumber(outputTokens)).append("\n");
        sb.append("  ").append(AnsiStyle.bold("Total Tokens:   ")).append(formatNumber(totalTokens)).append("\n\n");

        sb.append("  ").append(AnsiStyle.bold("Estimated Cost")).append("\n");
        sb.append("  Input:  $").append(String.format("%.4f", inputCost)).append(" (").append(formatNumber(inputTokens)).append(" × $3/M)\n");
        sb.append("  Output: $").append(String.format("%.4f", outputCost)).append(" (").append(formatNumber(outputTokens)).append(" × $15/M)\n");
        sb.append("  ").append(AnsiStyle.bold("Total:  $" + String.format("%.4f", totalCost))).append("\n\n");

        // Usage bar
        if (totalTokens > 0) {
            int barWidth = 30;
            int inputBar = (int) ((double) inputTokens / totalTokens * barWidth);
            int outputBar = barWidth - inputBar;
            sb.append("  [").append(AnsiStyle.blue("█".repeat(inputBar)))
                    .append(AnsiStyle.green("█".repeat(outputBar))).append("]\n");
            sb.append("  ").append(AnsiStyle.blue("■")).append(" Input  ")
                    .append(AnsiStyle.green("■")).append(" Output\n");
        }

        return sb.toString();
    }

    private String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
