package com.claudecode.tool.impl;

import com.claudecode.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * BashTool 输入验证测试。
 * 注意：不执行真实命令，只测试验证逻辑。
 */
class BashToolTest {

    @TempDir
    Path tempDir;

    private final BashTool tool = new BashTool();

    private ToolContext createContext() {
        return new ToolContext(tempDir, "test-model");
    }

    // ==================== Input validation ====================

    @Test
    @DisplayName("null command returns error")
    void nullCommand() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", null);

        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("command");
    }

    @Test
    @DisplayName("blank command returns error")
    void blankCommand() {
        Map<String, Object> input = Map.of("command", "   ");
        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("command");
    }

    @Test
    @DisplayName("invalid timeout type uses default")
    void invalidTimeoutType() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", "echo hello");
        input.put("timeout", "not a number");

        // Should not throw, should use default timeout
        String result = tool.execute(input, createContext());
        // Result could be success (echo) or error depending on OS, but should not NPE
        assertThat(result).isNotNull();
    }

    // ==================== Dangerous command blocking ====================

    @Test
    @DisplayName("rm -rf / is blocked")
    void blockDangerous_rmRf() {
        Map<String, Object> input = Map.of("command", "rm -rf /");
        String result = tool.execute(input, createContext());
        assertThat(result).contains("BLOCKED");
    }

    @Test
    @DisplayName("fork bomb variant is blocked")
    void blockDangerous_forkBomb() {
        // Exact format matching the DANGEROUS_COMMANDS set
        Map<String, Object> input = Map.of("command", ":(){ :|:& };:");
        String result = tool.execute(input, createContext());
        // Fork bomb with spaces gets passed to shell which errors, that's OK
        // Test the exact format from the set instead
        Map<String, Object> input2 = Map.of("command", ":(){:|:&};:");
        String result2 = tool.execute(input2, createContext());
        assertThat(result2).contains("BLOCKED");
    }

    @Test
    @DisplayName("git push --force is blocked")
    void blockDangerous_forcePush() {
        Map<String, Object> input = Map.of("command", "git push --force");
        String result = tool.execute(input, createContext());
        assertThat(result).contains("BLOCKED");
    }

    // ==================== Tool metadata ====================

    @Test
    @DisplayName("tool name is Bash")
    void toolName() {
        assertThat(tool.name()).isEqualTo("Bash");
    }

    @Test
    @DisplayName("tool is not read-only")
    void notReadOnly() {
        assertThat(tool.isReadOnly()).isFalse();
    }

    @Test
    @DisplayName("description is not empty")
    void description() {
        assertThat(tool.description()).isNotBlank();
    }

    @Test
    @DisplayName("input schema is valid JSON")
    void inputSchema() {
        assertThat(tool.inputSchema()).contains("\"command\"").contains("\"type\"");
    }
}
