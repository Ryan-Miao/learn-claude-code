package com.claudecode.tool.impl;

import com.claudecode.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * FileEditTool 验证测试。
 */
class FileEditToolTest {

    @TempDir
    Path tempDir;

    private final FileEditTool tool = new FileEditTool();

    private ToolContext createContext() {
        return new ToolContext(tempDir, "test-model");
    }

    // ==================== Input validation ====================

    @Test
    @DisplayName("null file_path returns error")
    void nullFilePath() {
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", null);
        input.put("old_string", "old");
        input.put("new_string", "new");

        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("file_path");
    }

    @Test
    @DisplayName("blank file_path returns error")
    void blankFilePath() {
        Map<String, Object> input = Map.of("file_path", "  ", "old_string", "old", "new_string", "new");
        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("file_path");
    }

    @Test
    @DisplayName("null old_string returns error")
    void nullOldString() {
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", "test.txt");
        input.put("old_string", null);
        input.put("new_string", "new");

        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("old_string");
    }

    @Test
    @DisplayName("null new_string returns error")
    void nullNewString() {
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", "test.txt");
        input.put("old_string", "old");
        input.put("new_string", null);

        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("new_string");
    }

    // ==================== Path traversal protection ====================

    @Test
    @DisplayName("path traversal is blocked")
    void pathTraversal_blocked() throws IOException {
        // Create a file outside tempDir
        Path outsideDir = tempDir.getParent().resolve("outside-test-" + System.nanoTime());
        Files.createDirectories(outsideDir);
        Path outsideFile = outsideDir.resolve("secret.txt");
        Files.writeString(outsideFile, "secret content");

        try {
            Map<String, Object> input = Map.of(
                    "file_path", "../" + outsideDir.getFileName() + "/secret.txt",
                    "old_string", "secret",
                    "new_string", "hacked"
            );

            String result = tool.execute(input, createContext());
            assertThat(result).containsIgnoringCase("error").containsIgnoringCase("traversal");

            // Verify file was NOT modified
            assertThat(Files.readString(outsideFile)).isEqualTo("secret content");
        } finally {
            Files.deleteIfExists(outsideFile);
            Files.deleteIfExists(outsideDir);
        }
    }

    // ==================== Core functionality ====================

    @Test
    @DisplayName("successful edit replaces text")
    void successfulEdit() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "Hello World\nFoo Bar\nBaz");

        Map<String, Object> input = Map.of(
                "file_path", "hello.txt",
                "old_string", "Foo Bar",
                "new_string", "New Content"
        );

        String result = tool.execute(input, createContext());
        assertThat(result).contains("Edited");
        assertThat(Files.readString(file)).contains("New Content").doesNotContain("Foo Bar");
    }

    @Test
    @DisplayName("file not found returns error")
    void fileNotFound() {
        Map<String, Object> input = Map.of(
                "file_path", "nonexistent.txt",
                "old_string", "a",
                "new_string", "b"
        );

        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").containsIgnoringCase("not found");
    }

    @Test
    @DisplayName("old_string not found returns error")
    void oldStringNotFound() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello World");

        Map<String, Object> input = Map.of(
                "file_path", "test.txt",
                "old_string", "does not exist",
                "new_string", "replacement"
        );

        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("not found");
    }

    @Test
    @DisplayName("multiple matches returns error")
    void multipleMatches() throws IOException {
        Path file = tempDir.resolve("dup.txt");
        Files.writeString(file, "hello\nhello\nhello");

        Map<String, Object> input = Map.of(
                "file_path", "dup.txt",
                "old_string", "hello",
                "new_string", "world"
        );

        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").containsIgnoringCase("multiple");
    }
}
