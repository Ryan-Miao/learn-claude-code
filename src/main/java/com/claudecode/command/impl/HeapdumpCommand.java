package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.SlashCommand;
import com.claudecode.console.AnsiStyle;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * /heapdump 命令 —— JVM 堆转储（Java 独有优势）。
 * 使用 HotSpotDiagnosticMXBean 进行堆转储。
 */
public class HeapdumpCommand implements SlashCommand {

    @Override
    public String name() { return "heapdump"; }

    @Override
    public String description() { return "Generate JVM heap dump (Java advantage)"; }

    @Override
    public String execute(String args, CommandContext context) {
        String trimmed = (args == null) ? "" : args.trim();

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(AnsiStyle.bold("  📦 JVM Heap Dump\n"));
        sb.append("  ").append("─".repeat(40)).append("\n\n");

        if (trimmed.equals("info") || trimmed.isEmpty()) {
            // Show memory pool details
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

            sb.append(AnsiStyle.bold("  Heap Memory\n"));
            sb.append("  Used:      ").append(formatBytes(heap.getUsed())).append("\n");
            sb.append("  Committed: ").append(formatBytes(heap.getCommitted())).append("\n");
            sb.append("  Max:       ").append(formatBytes(heap.getMax())).append("\n\n");

            sb.append(AnsiStyle.bold("  Non-Heap Memory\n"));
            sb.append("  Used:      ").append(formatBytes(nonHeap.getUsed())).append("\n");
            sb.append("  Committed: ").append(formatBytes(nonHeap.getCommitted())).append("\n\n");

            sb.append(AnsiStyle.bold("  Memory Pools\n"));
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                MemoryUsage usage = pool.getUsage();
                if (usage != null && usage.getUsed() > 0) {
                    sb.append("  ").append(String.format("%-25s", pool.getName()))
                            .append(formatBytes(usage.getUsed())).append("\n");
                }
            }

            sb.append("\n").append(AnsiStyle.dim("  Run /heapdump dump to generate a heap dump file"));

        } else if (trimmed.startsWith("dump")) {
            // Determine output path
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = trimmed.length() > 5 ? trimmed.substring(5).trim() : "";
            if (filename.isEmpty()) {
                filename = "heapdump-" + timestamp + ".hprof";
            }
            Path dumpPath = Path.of(System.getProperty("user.dir"), filename);

            try {
                var hotspot = ManagementFactory.getPlatformMXBean(
                        com.sun.management.HotSpotDiagnosticMXBean.class);
                hotspot.dumpHeap(dumpPath.toString(), true);
                long fileSize = dumpPath.toFile().length();
                sb.append("  ✅ Heap dump saved to:\n");
                sb.append("  ").append(AnsiStyle.cyan(dumpPath.toString())).append("\n");
                sb.append("  Size: ").append(formatBytes(fileSize)).append("\n\n");
                sb.append(AnsiStyle.dim("  Analyze with: jhat, MAT, or VisualVM"));
            } catch (Exception e) {
                sb.append("  ❌ Failed to create heap dump: ").append(e.getMessage()).append("\n");
                sb.append(AnsiStyle.dim("  Requires HotSpot JVM (OpenJDK or Oracle JDK)"));
            }

        } else if (trimmed.equals("gc")) {
            // Trigger GC and report
            long beforeUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            System.gc();
            long afterUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long freed = beforeUsed - afterUsed;

            sb.append("  🗑 Garbage collection triggered\n");
            sb.append("  Before: ").append(formatBytes(beforeUsed)).append("\n");
            sb.append("  After:  ").append(formatBytes(afterUsed)).append("\n");
            sb.append("  Freed:  ").append(AnsiStyle.green(formatBytes(Math.max(0, freed)))).append("\n");

        } else {
            sb.append(AnsiStyle.bold("  Subcommands\n"));
            sb.append("  /heapdump         Show memory pool info\n");
            sb.append("  /heapdump dump    Generate .hprof file\n");
            sb.append("  /heapdump gc      Trigger garbage collection\n");
        }

        return sb.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes >= 1_073_741_824) return String.format("%.1fGB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576) return String.format("%.1fMB", bytes / 1_048_576.0);
        return String.format("%.0fKB", bytes / 1_024.0);
    }
}
