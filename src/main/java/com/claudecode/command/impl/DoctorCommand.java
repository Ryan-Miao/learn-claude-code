package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

import java.io.File;
import java.util.List;

/**
 * /doctor 命令 —— 环境诊断。
 * <p>
 * 对应 claude-code 的 /doctor 命令，检查运行环境是否配置正确。
 * 检查项：
 * <ul>
 *   <li>Java 版本</li>
 *   <li>Git 版本和配置</li>
 *   <li>环境变量（API key 等）</li>
 *   <li>配置文件状态</li>
 *   <li>MCP 服务器连接</li>
 *   <li>磁盘空间</li>
 * </ul>
 */
public class DoctorCommand extends BaseSlashCommand {

    @Override
    public String name() {
        return "doctor";
    }

    @Override
    public String description() {
        return "Run environment diagnostics";
    }

    @Override
    public List<String> aliases() {
        return List.of("diag", "check");
    }

    @Override
    public String execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(AnsiStyle.bold("  🩺 Environment Diagnostics\n"));
        sb.append("  ").append("═".repeat(50)).append("\n\n");

        int passed = 0, warned = 0, failed = 0;

        // Java Version
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        boolean javaOk = javaVersion != null && (javaVersion.startsWith("21")
                || javaVersion.startsWith("22") || javaVersion.startsWith("23")
                || javaVersion.startsWith("24") || javaVersion.startsWith("25"));
        sb.append(check("Java", javaVersion + " (" + javaVendor + ")", javaOk));
        if (javaOk) passed++; else warned++;

        // OS Info
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        sb.append(check("OS", osName + " " + osVersion + " (" + osArch + ")", true));
        passed++;

        // Working Directory
        String workDir = System.getProperty("user.dir");
        sb.append(check("Working Dir", workDir, true));
        passed++;

        // Git
        String gitVersion = runCommand("git", "--version");
        boolean gitOk = gitVersion != null && gitVersion.contains("git version");
        sb.append(check("Git", gitOk ? gitVersion.strip() : "not found", gitOk));
        if (gitOk) passed++; else failed++;

        // Git config
        if (gitOk) {
            String userName = runCommand("git", "config", "user.name");
            String userEmail = runCommand("git", "config", "user.email");
            boolean configOk = userName != null && !userName.isBlank()
                    && userEmail != null && !userEmail.isBlank();
            String configInfo = configOk
                    ? userName.strip() + " <" + userEmail.strip() + ">"
                    : "user.name or user.email not set";
            sb.append(check("Git Config", configInfo, configOk));
            if (configOk) passed++; else warned++;
        }

        // API Key
        boolean hasAnthropicKey = System.getenv("ANTHROPIC_API_KEY") != null;
        boolean hasOpenAiKey = System.getenv("OPENAI_API_KEY") != null;
        boolean hasApiKey = hasAnthropicKey || hasOpenAiKey;
        String apiKeyInfo = hasAnthropicKey ? "ANTHROPIC_API_KEY set"
                : hasOpenAiKey ? "OPENAI_API_KEY set" : "No API key found";
        sb.append(check("API Key", apiKeyInfo, hasApiKey));
        if (hasApiKey) passed++; else failed++;

        // CLAUDE.md
        boolean hasClaudeMd = new File(workDir, "CLAUDE.md").exists();
        sb.append(check("CLAUDE.md", hasClaudeMd ? "found" : "not found (optional)", true));
        passed++;

        // Config directory
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ".claude-code");
        boolean hasConfigDir = configDir.exists() && configDir.isDirectory();
        sb.append(check("Config Dir", configDir.getPath()
                + (hasConfigDir ? " (exists)" : " (will be created)"), true));
        passed++;

        // MCP config
        boolean hasProjectMcp = new File(workDir, ".mcp.json").exists();
        boolean hasGlobalMcp = new File(userHome, ".claude-code-java/mcp.json").exists();
        String mcpInfo = hasProjectMcp ? ".mcp.json found (project)"
                : hasGlobalMcp ? "mcp.json found (global)" : "no config";
        sb.append(check("MCP Config", mcpInfo, true));
        passed++;

        // Disk Space
        File root = new File(workDir);
        long freeSpace = root.getFreeSpace();
        long totalSpace = root.getTotalSpace();
        double freeGb = freeSpace / (1024.0 * 1024.0 * 1024.0);
        double totalGb = totalSpace / (1024.0 * 1024.0 * 1024.0);
        boolean diskOk = freeGb > 1.0;
        sb.append(check("Disk Space", String.format("%.1f GB free / %.1f GB total", freeGb, totalGb), diskOk));
        if (diskOk) passed++; else warned++;

        // Memory
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        sb.append(check("JVM Memory", usedMem + " MB used / " + maxMem + " MB max", maxMem > 256));
        passed++;

        // Summary
        sb.append("\n  ").append("─".repeat(50)).append("\n");
        sb.append("  ").append(AnsiStyle.bold("Summary: "));
        sb.append(AnsiStyle.green(passed + " passed"));
        if (warned > 0) sb.append(", ").append(AnsiStyle.yellow(warned + " warnings"));
        if (failed > 0) sb.append(", ").append(AnsiStyle.red(failed + " failed"));
        sb.append("\n");

        return sb.toString();
    }

    private String check(String label, String value, boolean ok) {
        String icon = ok ? AnsiStyle.green("✓") : AnsiStyle.red("✗");
        return "  " + icon + " " + AnsiStyle.bold(String.format("%-14s", label))
                + " " + value + "\n";
    }

    private String runCommand(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).strip();
            int exit = p.waitFor();
            return exit == 0 ? output : null;
        } catch (Exception e) {
            return null;
        }
    }
}
