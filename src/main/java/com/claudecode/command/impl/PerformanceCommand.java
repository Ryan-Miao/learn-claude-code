package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.SlashCommand;
import com.claudecode.console.AnsiStyle;
import com.claudecode.telemetry.MetricsCollector;

/**
 * /performance 命令 —— 性能统计。
 */
public class PerformanceCommand implements SlashCommand {

    @Override
    public String name() { return "performance"; }

    @Override
    public String description() { return "Show performance statistics"; }

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("perf");
    }

    @Override
    public String execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(AnsiStyle.bold("  ⚡ Performance Statistics\n"));
        sb.append("  ").append("─".repeat(40)).append("\n\n");

        // JVM stats
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;
        long maxMem = runtime.maxMemory();

        sb.append(AnsiStyle.bold("  Memory\n"));
        sb.append("  Used:      ").append(formatBytes(usedMem)).append("\n");
        sb.append("  Allocated: ").append(formatBytes(totalMem)).append("\n");
        sb.append("  Max:       ").append(formatBytes(maxMem)).append("\n");
        sb.append("  Usage:     ").append(memBar(usedMem, maxMem)).append("\n\n");

        // Thread stats
        int threadCount = Thread.activeCount();
        sb.append(AnsiStyle.bold("  Threads\n"));
        sb.append("  Active:    ").append(threadCount).append("\n");
        sb.append("  Available: ").append(runtime.availableProcessors()).append(" CPUs\n\n");

        // GC stats
        long gcCount = 0;
        long gcTime = 0;
        for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gc.getCollectionCount();
            gcTime += gc.getCollectionTime();
        }
        sb.append(AnsiStyle.bold("  GC\n"));
        sb.append("  Collections: ").append(gcCount).append("\n");
        sb.append("  Total time:  ").append(gcTime).append("ms\n\n");

        // Metrics if available
        if (context.agentLoop() != null) {
            Object metricsObj = context.agentLoop().getToolContext().get("METRICS_COLLECTOR");
            if (metricsObj instanceof MetricsCollector metrics) {
                sb.append(AnsiStyle.bold("  Session Metrics\n"));
                sb.append("  Duration:    ").append(formatDuration(metrics.getSessionDurationSeconds())).append("\n");
                var toolUsage = metrics.getToolUsage();
                if (!toolUsage.isEmpty()) {
                    sb.append("  Tool calls:  ").append(toolUsage.values().stream().mapToLong(Long::longValue).sum()).append("\n");
                    sb.append("  Top tools:   ");
                    toolUsage.entrySet().stream()
                            .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(3)
                            .forEach(e -> sb.append(e.getKey()).append("(").append(e.getValue()).append(") "));
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    private String memBar(long used, long max) {
        int barWidth = 20;
        double ratio = (double) used / max;
        int filled = (int) (ratio * barWidth);
        String color = ratio > 0.8 ? AnsiStyle.red("█".repeat(filled))
                : ratio > 0.5 ? AnsiStyle.yellow("█".repeat(filled))
                : AnsiStyle.green("█".repeat(filled));
        return "[" + color + "░".repeat(barWidth - filled) + "] " +
                String.format("%.0f%%", ratio * 100);
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1_073_741_824) return String.format("%.1fGB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576) return String.format("%.0fMB", bytes / 1_048_576.0);
        return String.format("%.0fKB", bytes / 1_024.0);
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}
