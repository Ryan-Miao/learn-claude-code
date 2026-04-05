package com.claudecode.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * InternalLogger 单元测试。
 */
class InternalLoggerTest {

    @TempDir
    Path tempDir;

    private InternalLogger createLogger() {
        return new InternalLogger("test-session", tempDir);
    }

    // ==================== Basic logging ====================

    @Test
    @DisplayName("info log is recorded")
    void info_recorded() {
        InternalLogger logger = createLogger();
        logger.info("TEST", "hello world");

        String recent = logger.getRecent(10);
        assertThat(recent).contains("TEST").contains("hello world");
    }

    @Test
    @DisplayName("debug log filtered when level is NORMAL")
    void debug_filteredAtNormal() {
        InternalLogger logger = createLogger();
        logger.setLevel(InternalLogger.Level.NORMAL);
        logger.debug("TEST", "secret debug info");

        String recent = logger.getRecent(10);
        assertThat(recent).doesNotContain("secret debug info");
    }

    @Test
    @DisplayName("debug log visible when level is DEBUG")
    void debug_visibleAtDebug() {
        InternalLogger logger = createLogger();
        logger.setLevel(InternalLogger.Level.DEBUG);
        logger.debug("TEST", "debug info");

        String recent = logger.getRecent(10);
        assertThat(recent).contains("debug info");
    }

    @Test
    @DisplayName("verbose log visible when level is VERBOSE")
    void verbose_visibleAtVerbose() {
        InternalLogger logger = createLogger();
        logger.setLevel(InternalLogger.Level.VERBOSE);
        logger.verbose("TOOL", "verbose detail");

        String recent = logger.getRecent(10);
        assertThat(recent).contains("verbose detail");
    }

    // ==================== Structured logging ====================

    @Test
    @DisplayName("toolCall creates structured log entry")
    void toolCall_structured() {
        InternalLogger logger = createLogger();
        logger.setLevel(InternalLogger.Level.VERBOSE);
        logger.toolCall("bash", "ls -la", "file.txt", 150);

        String recent = logger.getRecent(10);
        assertThat(recent).contains("TOOL").contains("bash").contains("150ms");
    }

    @Test
    @DisplayName("apiCall creates structured log entry")
    void apiCall_structured() {
        InternalLogger logger = createLogger();
        logger.apiCall("sonnet", 1000, 500, 2000);

        String recent = logger.getRecent(10);
        assertThat(recent).contains("API").contains("sonnet");
    }

    @Test
    @DisplayName("error includes exception info")
    void error_withException() {
        InternalLogger logger = createLogger();
        logger.error("NET", "connection failed", new IOException("timeout"));

        String recent = logger.getRecent(10);
        assertThat(recent).contains("ERROR:NET").contains("IOException").contains("timeout");
    }

    // ==================== Entry count and limits ====================

    @Test
    @DisplayName("entry count tracks all recorded entries")
    void entryCount() {
        InternalLogger logger = createLogger();
        logger.info("A", "one");
        logger.info("B", "two");
        logger.info("C", "three");

        assertThat(logger.getEntryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getRecent limits returned entries")
    void getRecent_limited() {
        InternalLogger logger = createLogger();
        for (int i = 0; i < 10; i++) {
            logger.info("TEST", "entry " + i);
        }

        String last3 = logger.getRecent(3);
        assertThat(last3).contains("entry 9").contains("entry 8").contains("entry 7");
        assertThat(last3).doesNotContain("entry 0");
    }

    // ==================== File output ====================

    @Test
    @DisplayName("log entries are written to file")
    void fileOutput() throws IOException {
        InternalLogger logger = createLogger();
        logger.info("FILE", "written to disk");

        // Check log dir has files
        long fileCount = Files.list(tempDir).count();
        assertThat(fileCount).isGreaterThanOrEqualTo(1);

        // Read the file content
        String content = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".log"))
                .findFirst()
                .map(p -> { try { return Files.readString(p); } catch (IOException e) { return ""; } })
                .orElse("");
        assertThat(content).contains("FILE").contains("written to disk");
    }

    // ==================== Export ====================

    @Test
    @DisplayName("export writes all entries to target file")
    void export() throws IOException {
        InternalLogger logger = createLogger();
        logger.info("A", "first");
        logger.info("B", "second");

        Path exportFile = tempDir.resolve("export.log");
        logger.export(exportFile);

        String content = Files.readString(exportFile);
        assertThat(content)
                .contains("Session Log: test-session")
                .contains("first")
                .contains("second");
    }

    // ==================== Configuration ====================

    @Test
    @DisplayName("level getter/setter works")
    void levelGetSet() {
        InternalLogger logger = createLogger();
        assertThat(logger.getLevel()).isEqualTo(InternalLogger.Level.NORMAL);

        logger.setLevel(InternalLogger.Level.DEBUG);
        assertThat(logger.getLevel()).isEqualTo(InternalLogger.Level.DEBUG);
    }

    @Test
    @DisplayName("session ID is accessible")
    void sessionId() {
        InternalLogger logger = createLogger();
        assertThat(logger.getSessionId()).isEqualTo("test-session");
    }
}
