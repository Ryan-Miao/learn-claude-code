package com.claudecode.command.impl;

import com.claudecode.command.CommandContext;
import com.claudecode.command.BaseSlashCommand;
import com.claudecode.console.AnsiStyle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * /feedback 命令 —— 提交反馈（本地保存）。
 */
public class FeedbackCommand extends BaseSlashCommand {

    @Override
    public String name() { return "feedback"; }

    @Override
    public String description() { return "Submit feedback (saved locally)"; }

    @Override
    public String execute(String args, CommandContext context) {
        String trimmed = args(args);

        if (trimmed.isEmpty()) {
            return AnsiStyle.yellow("  Usage: /feedback <your feedback text>")
                    + "\n" + AnsiStyle.dim("  Feedback is saved locally to ~/.claude-code-java/feedback/");
        }

        try {
            Path feedbackDir = Path.of(System.getProperty("user.home"),
                    ".claude-code-java", "feedback");
            Files.createDirectories(feedbackDir);

            String timestamp = Instant.now().toString().replaceAll("[:]", "-");
            Path feedbackFile = feedbackDir.resolve("feedback-" + timestamp + ".txt");

            StringBuilder content = new StringBuilder();
            content.append("Timestamp: ").append(Instant.now()).append("\n");
            content.append("OS: ").append(System.getProperty("os.name")).append("\n");
            content.append("Java: ").append(System.getProperty("java.version")).append("\n");
            content.append("WorkDir: ").append(System.getProperty("user.dir")).append("\n");
            content.append("---\n");
            content.append(trimmed).append("\n");

            Files.writeString(feedbackFile, content.toString());

            return AnsiStyle.green("  ✓ Feedback saved: " + feedbackFile.getFileName())
                    + "\n" + AnsiStyle.dim("  Thank you for your feedback!");
        } catch (IOException e) {
            return AnsiStyle.red("  ✗ Failed to save feedback: " + e.getMessage());
        }
    }
}
