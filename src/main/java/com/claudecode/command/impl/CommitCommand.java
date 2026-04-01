package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.SlashCommand;
import com.claudecode.console.AnsiStyle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * /commit 命令 —— 创建 Git commit。
 * <p>
 * 支持多种模式：
 * <ul>
 *   <li>/commit —— 自动生成 AI commit message（基于 git diff）</li>
 *   <li>/commit [message] —— 使用指定的 commit message</li>
 *   <li>/commit --all —— 添加所有文件并提交</li>
 * </ul>
 */
public class CommitCommand implements SlashCommand {

    @Override
    public String name() {
        return "commit";
    }

    @Override
    public String description() {
        return "Create a git commit (with optional AI-generated message)";
    }

    @Override
    public String execute(String args, CommandContext context) {
        Path projectDir = Path.of(System.getProperty("user.dir"));
        if (!Files.isDirectory(projectDir.resolve(".git"))) {
            return AnsiStyle.yellow("  ⚠ 当前目录不是 Git 仓库");
        }

        args = args == null ? "" : args.strip();

        try {
            boolean addAll = args.contains("--all") || args.contains("-a");
            String message = args.replaceAll("--all|-a", "").strip();

            // --all 模式：先执行 git add -A
            if (addAll) {
                String addResult = runGit(projectDir, "add", "-A");
                if (addResult == null) {
                    return AnsiStyle.red("  ✗ git add 失败");
                }
            }

            // 检查是否有已暂存的变更
            String staged = runGit(projectDir, "diff", "--cached", "--stat");
            if (staged == null || staged.isBlank()) {
                String status = runGit(projectDir, "status", "--short");
                if (status != null && !status.isBlank()) {
                    return AnsiStyle.yellow("  ⚠ 没有已暂存的变更\n")
                            + AnsiStyle.dim("  使用 /commit --all 自动添加所有文件\n")
                            + AnsiStyle.dim("  或先手动执行 git add");
                }
                return AnsiStyle.green("  ✓ 工作区干净，无需提交");
            }

            // 如果没有指定 message，使用 AI 生成
            if (message.isEmpty()) {
                message = generateCommitMessage(projectDir, context);
                if (message == null || message.isBlank()) {
                    return AnsiStyle.red("  ✗ 无法生成 commit message");
                }
            }

            // 执行 git commit
            String commitResult = runGit(projectDir, "commit", "-m", message);
            if (commitResult == null) {
                return AnsiStyle.red("  ✗ git commit 失败");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n").append(AnsiStyle.green("  ✓ Commit 成功\n"));
            sb.append("  ").append("─".repeat(50)).append("\n");
            sb.append("  ").append(AnsiStyle.bold("Message: ")).append(message).append("\n");

            // 显示提交摘要
            commitResult.lines().forEach(line -> sb.append("  ").append(AnsiStyle.dim(line)).append("\n"));

            return sb.toString();

        } catch (Exception e) {
            return AnsiStyle.red("  ✗ 提交失败: " + e.getMessage());
        }
    }

    /** 使用 AI 分析 git diff 生成 commit message */
    private String generateCommitMessage(Path projectDir, CommandContext context) {
        try {
            // 获取暂存区的 diff
            String diff = runGit(projectDir, "diff", "--cached");
            if (diff == null || diff.isBlank()) return null;

            // 截断过长的 diff
            if (diff.length() > 4000) {
                diff = diff.substring(0, 4000) + "\n... (diff truncated)";
            }

            // 使用 ChatModel 生成 commit message
            String prompt = """
                    分析以下 git diff，生成一个简洁的 commit message。
                    要求：
                    1. 使用 conventional commits 格式（feat/fix/docs/refactor/chore等前缀）
                    2. 第一行不超过 72 个字符
                    3. 如果有多个变更，可以在第一行后空一行添加详细说明
                    4. 只返回 commit message 文本，不要添加其他说明
                    
                    Git diff:
                    ```
                    %s
                    ```
                    """.formatted(diff);

            var chatModel = context.agentLoop().getChatModel();
            var response = chatModel.call(
                    new org.springframework.ai.chat.prompt.Prompt(prompt));

            String generated = response.getResult().getOutput().getText();
            if (generated != null) {
                // 清理：去除可能的引号和多余空行
                generated = generated.strip()
                        .replaceAll("^[\"'`]+|[\"'`]+$", "")
                        .strip();
            }
            return generated;

        } catch (Exception e) {
            // AI 生成失败时返回默认消息
            return null;
        }
    }

    private String runGit(Path dir, String... args) {
        try {
            var command = new java.util.ArrayList<String>();
            command.add("git");
            command.add("--no-pager");
            command.addAll(java.util.List.of(args));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            return output.toString().stripTrailing();
        } catch (Exception e) {
            return null;
        }
    }
}
