package com.claudecode.command.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * 命令基本属性测试 —— 验证所有 Phase 4 命令的元数据。
 */
class Phase4CommandsTest {

    // ==================== Phase 4B Commands ====================

    @Test
    @DisplayName("BriefCommand metadata")
    void briefCommand() {
        BriefCommand cmd = new BriefCommand();
        assertThat(cmd.name()).isEqualTo("brief");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    @DisplayName("VimCommand metadata")
    void vimCommand() {
        VimCommand cmd = new VimCommand();
        assertThat(cmd.name()).isEqualTo("vim");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    @DisplayName("ThemeCommand metadata")
    void themeCommand() {
        ThemeCommand cmd = new ThemeCommand();
        assertThat(cmd.name()).isEqualTo("theme");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    @DisplayName("UsageCommand metadata")
    void usageCommand() {
        UsageCommand cmd = new UsageCommand();
        assertThat(cmd.name()).isEqualTo("usage");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    @DisplayName("TipsCommand metadata")
    void tipsCommand() {
        TipsCommand cmd = new TipsCommand();
        assertThat(cmd.name()).isEqualTo("tips");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    @DisplayName("OutputStyleCommand metadata")
    void outputStyleCommand() {
        OutputStyleCommand cmd = new OutputStyleCommand();
        assertThat(cmd.name()).isEqualTo("output-style");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    @DisplayName("EnvCommand shows system info without agent loop")
    void envCommand_noLoop() {
        EnvCommand cmd = new EnvCommand();
        assertThat(cmd.name()).isEqualTo("env");
        String result = cmd.execute(null, new com.claudecode.command.CommandContext(null, null, null, null, null));
        assertThat(result).contains("Environment");
    }

    @Test
    @DisplayName("PerformanceCommand shows JVM stats")
    void performanceCommand() {
        PerformanceCommand cmd = new PerformanceCommand();
        assertThat(cmd.name()).isEqualTo("performance");
        assertThat(cmd.aliases()).contains("perf");

        String result = cmd.execute(null, new com.claudecode.command.CommandContext(null, null, null, null, null));
        assertThat(result).contains("Memory").contains("Threads");
    }

    @Test
    @DisplayName("KeybindingsCommand shows shortcuts")
    void keybindingsCommand() {
        KeybindingsCommand cmd = new KeybindingsCommand();
        assertThat(cmd.name()).isEqualTo("keybindings");
        String result = cmd.execute(null, new com.claudecode.command.CommandContext(null, null, null, null, null));
        assertThat(result).contains("Keyboard");
    }

    // ==================== Phase 4D Commands ====================

    @Test
    @DisplayName("DebugCommand metadata and aliases")
    void debugCommand() {
        DebugCommand cmd = new DebugCommand();
        assertThat(cmd.name()).isEqualTo("debug");
        assertThat(cmd.aliases()).contains("dbg");
    }

    @Test
    @DisplayName("HeapdumpCommand shows memory info")
    void heapdumpCommand() {
        HeapdumpCommand cmd = new HeapdumpCommand();
        assertThat(cmd.name()).isEqualTo("heapdump");

        String result = cmd.execute("info", new com.claudecode.command.CommandContext(null, null, null, null, null));
        assertThat(result).contains("Heap Memory");
    }

    @Test
    @DisplayName("TraceCommand metadata")
    void traceCommand() {
        TraceCommand cmd = new TraceCommand();
        assertThat(cmd.name()).isEqualTo("trace");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    @DisplayName("ContextVizCommand metadata and aliases")
    void contextVizCommand() {
        ContextVizCommand cmd = new ContextVizCommand();
        assertThat(cmd.name()).isEqualTo("ctx-viz");
        assertThat(cmd.aliases()).contains("context", "ctx");
    }

    @Test
    @DisplayName("ResetLimitsCommand metadata and aliases")
    void resetLimitsCommand() {
        ResetLimitsCommand cmd = new ResetLimitsCommand();
        assertThat(cmd.name()).isEqualTo("reset-limits");
        assertThat(cmd.aliases()).contains("rl");
    }

    @Test
    @DisplayName("SandboxCommand metadata and status display")
    void sandboxCommand() {
        SandboxCommand cmd = new SandboxCommand();
        assertThat(cmd.name()).isEqualTo("sandbox");
        assertThat(cmd.description()).isNotBlank();
    }
}
