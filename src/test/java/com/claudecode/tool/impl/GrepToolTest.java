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
 * GrepTool 输入验证测试。
 */
class GrepToolTest {

    @TempDir
    Path tempDir;

    private final GrepTool tool = new GrepTool();

    private ToolContext createContext() {
        return new ToolContext(tempDir, "test-model");
    }

    // ==================== Input validation ====================

    @Test
    @DisplayName("null pattern returns error")
    void nullPattern() {
        Map<String, Object> input = new HashMap<>();
        input.put("pattern", null);

        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("pattern");
    }

    @Test
    @DisplayName("blank pattern returns error")
    void blankPattern() {
        Map<String, Object> input = Map.of("pattern", "   ");
        String result = tool.execute(input, createContext());
        assertThat(result).containsIgnoringCase("error").contains("pattern");
    }

    // ==================== Tool metadata ====================

    @Test
    @DisplayName("tool name is Grep")
    void toolName() {
        assertThat(tool.name()).isEqualTo("Grep");
    }

    @Test
    @DisplayName("tool is read-only")
    void readOnly() {
        assertThat(tool.isReadOnly()).isTrue();
    }

    @Test
    @DisplayName("description mentions ripgrep")
    void description() {
        assertThat(tool.description()).containsIgnoringCase("ripgrep");
    }

    @Test
    @DisplayName("input schema has pattern field")
    void inputSchema() {
        assertThat(tool.inputSchema()).contains("\"pattern\"");
    }
}
