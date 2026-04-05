package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Shell 工具 —— 对应 claude-code/src/tools/bash/BashTool.ts。
 * <p>
 * 在指定工作目录中执行 shell 命令，返回 stdout/stderr 输出。
 * 自动检测最佳可用 shell：
 * <ul>
 *   <li>Windows: PowerShell &gt; cmd.exe</li>
 *   <li>Unix/Linux/macOS: bash（或 SHELL 环境变量）</li>
 * </ul>
 */
public class BashTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);

    /** 默认超时（秒） */
    private static final int DEFAULT_TIMEOUT = 120;

    /**
     * 危险命令黑名单 —— 这些命令前缀需要特别警告。
     * 对应 TS 版 shouldUseSandbox.ts 的 containsExcludedCommand。
     * 注意：这不是安全边界，而是用户友好的提示机制。
     */
    private static final Set<String> DANGEROUS_COMMANDS = Set.of(
            "rm -rf /", "rm -rf /*", "rm -rf ~",
            "mkfs", "dd if=",
            ":(){:|:&};:",  // fork bomb
            "chmod -R 777 /",
            "git push --force", "git push -f",
            "git reset --hard",
            "shutdown", "reboot", "halt",
            "format c:", "del /f /s /q c:\\"
    );

    /**
     * 需要用户确认的命令前缀 —— 风险较高但不是完全禁止的操作。
     */
    private static final Set<String> WARN_COMMAND_PREFIXES = Set.of(
            "rm -rf", "rm -r",
            "git push --force", "git push -f",
            "git reset --hard",
            "git rebase",
            "drop database", "drop table",
            "truncate table",
            "sudo rm", "sudo dd"
    );

    /** 检测到的 shell 类型 */
    public enum ShellType {
        POWERSHELL("PowerShell", "pwsh", "-NoProfile", "-Command"),
        POWERSHELL_WINDOWS("PowerShell", "powershell.exe", "-NoProfile", "-Command"),
        CMD("cmd.exe", "cmd.exe", "/c", null),
        BASH("Bash", "bash", "-c", null),
        SH("sh", "sh", "-c", null);

        final String displayName;
        final String executable;
        final String flag1;
        final String flag2; // 可选的额外 flag

        ShellType(String displayName, String executable, String flag1, String flag2) {
            this.displayName = displayName;
            this.executable = executable;
            this.flag1 = flag1;
            this.flag2 = flag2;
        }
    }

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final ShellType DETECTED_SHELL = detectShell();
    private static final String SHELL_HINT = buildShellHint();

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        // Base description varies by platform
        String base;
        if (IS_WINDOWS && DETECTED_SHELL.displayName.equals("PowerShell")) {
            base = """
                Execute a command in the working directory using PowerShell. \
                Use PowerShell syntax (Get-ChildItem, Select-String, Get-Content, etc). \
                Common equivalents: ls→Get-ChildItem, grep→Select-String, cat→Get-Content, \
                rm→Remove-Item, cp→Copy-Item, mv→Move-Item, find→Get-ChildItem -Recurse.""";
        } else if (IS_WINDOWS) {
            base = """
                Execute a command in the working directory using cmd.exe. \
                Use Windows cmd syntax (dir, type, find, etc).""";
        } else {
            base = """
                Execute a bash command in the working directory. \
                Use this for running scripts, installing packages, or system commands.""";
        }

        // Shared behavioral guidance (adapted from TS BashTool/prompt.ts)
        return base + """

                Commands run in a subprocess with timeout protection. \
                Working directory persists between commands; shell state (variables, functions) does not.

                IMPORTANT RULES:
                - Do NOT use this tool when a dedicated tool exists. Use Read/Edit/Write for files, \
                Glob for finding files, Grep for searching content. Only use Bash for commands that \
                genuinely require shell execution (git, build tools, package managers, etc).
                - Be careful with destructive commands (rm -rf, git reset --hard, etc). These warrant \
                user confirmation.
                - When running long commands, consider the timeout setting.
                - For git operations: always use --no-pager to prevent interactive pagers that will hang. \
                Check git status before committing. Write clear, concise commit messages. Do NOT amend \
                commits or force-push without explicit user approval.
                - Prefer simple, targeted commands over complex pipelines when possible.
                - If a command fails, read the error carefully before retrying. Do not blindly retry \
                the same command.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "command": {
                  "type": "string",
                  "description": "The shell command to execute"
                },
                "timeout": {
                  "type": "integer",
                  "description": "Timeout in seconds (default: 120)"
                }
              },
              "required": ["command"]
            }""";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String command = (String) input.get("command");
        int timeout = input.containsKey("timeout")
                ? ((Number) input.get("timeout")).intValue()
                : DEFAULT_TIMEOUT;
        Path workDir = context.getWorkDir();

        // Sandbox check: block absolutely dangerous commands
        String cmdLower = command.toLowerCase().trim();
        for (String dangerous : DANGEROUS_COMMANDS) {
            if (cmdLower.equals(dangerous) || cmdLower.startsWith(dangerous)) {
                return "⛔ BLOCKED: This command is potentially destructive and has been blocked.\n"
                        + "Command: " + command + "\n"
                        + "If you really need to run this, please ask the user to execute it manually.";
            }
        }

        // Sandbox warning: flag risky commands
        for (String prefix : WARN_COMMAND_PREFIXES) {
            if (cmdLower.startsWith(prefix)) {
                log.warn("⚠️ Risky command detected: {}", command);
                break;
            }
        }

        try {
            ProcessBuilder pb = buildProcess(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    // 报告流式进度
                    context.reportProgress(line);
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return output + "\n[ERROR: Command timed out after " + timeout + " seconds]";
            }

            int exitCode = process.exitValue();
            String result = output.toString().stripTrailing();

            if (exitCode != 0) {
                return result + "\n[Exit code: " + exitCode + "]";
            }
            return result;

        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        String cmd = (String) input.getOrDefault("command", "");
        if (cmd.length() > 60) {
            cmd = cmd.substring(0, 57) + "...";
        }
        return "⚡ " + cmd;
    }

    /** 构建 ProcessBuilder，根据检测到的 shell 类型 */
    private ProcessBuilder buildProcess(String command) {
        return switch (DETECTED_SHELL) {
            case POWERSHELL -> new ProcessBuilder("pwsh", "-NoProfile", "-Command", command);
            case POWERSHELL_WINDOWS -> new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command);
            case CMD -> new ProcessBuilder("cmd.exe", "/c", command);
            case BASH -> new ProcessBuilder("bash", "-c", command);
            case SH -> new ProcessBuilder("sh", "-c", command);
        };
    }

    /** 检测最佳可用 shell */
    private static ShellType detectShell() {
        if (IS_WINDOWS) {
            // 优先 pwsh (PowerShell 7+)
            if (isCommandAvailable("pwsh", "--version")) {
                log.info("Detected shell: PowerShell 7+ (pwsh)");
                return ShellType.POWERSHELL;
            }
            // 回退到 Windows PowerShell 5.x
            if (isCommandAvailable("powershell.exe", "-NoProfile", "-Command", "echo ok")) {
                log.info("Detected shell: Windows PowerShell (powershell.exe)");
                return ShellType.POWERSHELL_WINDOWS;
            }
            // 最终回退到 cmd
            log.info("Detected shell: cmd.exe (fallback)");
            return ShellType.CMD;
        }

        // Unix: 优先 bash
        String shellEnv = System.getenv("SHELL");
        if (shellEnv != null && shellEnv.contains("bash")) {
            return ShellType.BASH;
        }
        if (isCommandAvailable("bash", "--version")) {
            return ShellType.BASH;
        }
        return ShellType.SH;
    }

    /** 检查命令是否可用 */
    private static boolean isCommandAvailable(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取 shell 提示信息（供系统提示词使用） */
    public static String getShellHint() {
        return SHELL_HINT;
    }

    /** 获取检测到的 shell 显示名 */
    public static String getDetectedShellName() {
        return DETECTED_SHELL.displayName;
    }

    private static String buildShellHint() {
        if (IS_WINDOWS && DETECTED_SHELL.displayName.equals("PowerShell")) {
            return """
                - Shell: %s (detected on Windows)
                - IMPORTANT: The Bash tool executes commands via PowerShell, NOT bash or cmd.exe.
                - Use PowerShell native cmdlets and syntax:
                  - List files: Get-ChildItem (or ls/dir aliases)
                  - Search text: Select-String -Pattern "xxx" -Path *.java
                  - Read file: Get-Content file.txt
                  - Delete: Remove-Item path
                  - Copy: Copy-Item src dst
                  - Move: Move-Item src dst
                  - Find files: Get-ChildItem -Recurse -Filter "*.java"
                  - Current dir: Get-Location (or pwd)
                  - Environment vars: $env:PATH
                  - Pipe: cmd1 | cmd2
                  - String comparison: -eq, -ne, -like, -match
                - Standard tools (git, mvn, npm, java, python) work normally.
                """.formatted(DETECTED_SHELL.displayName);
        } else if (IS_WINDOWS) {
            return """
                - Shell: cmd.exe (Windows)
                - Use Windows cmd syntax: dir, type, find, etc.
                - Standard tools (git, mvn, npm) work normally.
                """;
        }
        return "- Shell: " + DETECTED_SHELL.displayName + "\n";
    }
}
