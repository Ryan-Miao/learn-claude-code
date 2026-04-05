package com.claudecode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内部日志服务 —— 对应 claude-code 中 internalLogging。
 * <p>
 * 功能：
 * <ul>
 *   <li>结构化会话日志（独立于 SLF4J）</li>
 *   <li>可调试级别（normal/verbose/debug）</li>
 *   <li>导出为文本文件</li>
 *   <li>最近 N 条日志内存缓存（用于 /debug 命令）</li>
 *   <li>日志文件按天分割</li>
 * </ul>
 * <p>
 * 存储位置: ~/.claude-code-java/logs/{date}.log
 */
public class InternalLogger {

    private static final Logger log = LoggerFactory.getLogger(InternalLogger.class);

    public enum Level { NORMAL, VERBOSE, DEBUG }

    private final Path logDir;
    private Level currentLevel = Level.NORMAL;
    private final String sessionId;

    /** 最近日志缓存（用于 /debug 查看） */
    private final ConcurrentLinkedDeque<LogEntry> recentLogs = new ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT = 200;

    private final AtomicInteger entryCount = new AtomicInteger(0);

    public InternalLogger(String sessionId) {
        this(sessionId, Path.of(System.getProperty("user.home"), ".claude-code-java", "logs"));
    }

    public InternalLogger(String sessionId, Path logDir) {
        this.sessionId = sessionId;
        this.logDir = logDir;
    }

    // ==================== 日志记录 ====================

    public void info(String category, String message) {
        record(Level.NORMAL, category, message);
    }

    public void verbose(String category, String message) {
        record(Level.VERBOSE, category, message);
    }

    public void debug(String category, String message) {
        record(Level.DEBUG, category, message);
    }

    public void toolCall(String toolName, String input, String output, long durationMs) {
        String msg = String.format("tool=%s duration=%dms input_len=%d output_len=%d",
                toolName, durationMs,
                input != null ? input.length() : 0,
                output != null ? output.length() : 0);
        record(Level.VERBOSE, "TOOL", msg);
    }

    public void apiCall(String model, long inputTokens, long outputTokens, long durationMs) {
        String msg = String.format("model=%s input=%d output=%d duration=%dms",
                model, inputTokens, outputTokens, durationMs);
        record(Level.NORMAL, "API", msg);
    }

    public void error(String category, String message, Throwable throwable) {
        String msg = throwable != null
                ? message + " — " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage()
                : message;
        record(Level.NORMAL, "ERROR:" + category, msg);
    }

    public void command(String commandName, String args) {
        record(Level.VERBOSE, "CMD", "/" + commandName + (args != null ? " " + args : ""));
    }

    public void permission(String toolName, String decision) {
        record(Level.VERBOSE, "PERM", toolName + " → " + decision);
    }

    // ==================== 核心记录方法 ====================

    private void record(Level level, String category, String message) {
        if (level.ordinal() > currentLevel.ordinal()) return;

        LogEntry entry = new LogEntry(
                Instant.now(), level, category, message, sessionId);

        // 内存缓存
        recentLogs.addLast(entry);
        while (recentLogs.size() > MAX_RECENT) {
            recentLogs.pollFirst();
        }
        entryCount.incrementAndGet();

        // 文件写入（异步友好 — 简单同步追加）
        appendToFile(entry);
    }

    private void appendToFile(LogEntry entry) {
        try {
            Files.createDirectories(logDir);
            String date = LocalDate.now(ZoneId.systemDefault()).toString();
            Path file = logDir.resolve(date + ".log");
            String line = entry.format() + "\n";
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 静默失败 — 不能因为日志写入失败影响主流程
        }
    }

    // ==================== 查询 ====================

    /**
     * 获取最近 N 条日志。
     */
    public String getRecent(int count) {
        StringBuilder sb = new StringBuilder();
        var entries = recentLogs.stream()
                .skip(Math.max(0, recentLogs.size() - count))
                .toList();
        for (LogEntry entry : entries) {
            sb.append(entry.format()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 导出完整日志到文件。
     */
    public Path export(Path targetFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Session Log: ").append(sessionId).append("\n");
        sb.append("# Exported: ").append(Instant.now()).append("\n\n");
        for (LogEntry entry : recentLogs) {
            sb.append(entry.format()).append("\n");
        }
        Files.writeString(targetFile, sb.toString());
        return targetFile;
    }

    // ==================== 配置 ====================

    public void setLevel(Level level) {
        this.currentLevel = level;
        log.info("Internal log level set to {}", level);
    }

    public Level getLevel() { return currentLevel; }
    public int getEntryCount() { return entryCount.get(); }
    public String getSessionId() { return sessionId; }

    // ==================== 日志条目 ====================

    public record LogEntry(
            Instant timestamp,
            Level level,
            String category,
            String message,
            String sessionId
    ) {
        public String format() {
            String time = timestamp.toString().substring(11, 23); // HH:mm:ss.SSS
            return String.format("[%s] %s [%s] %s", time, level.name().charAt(0), category, message);
        }
    }
}
