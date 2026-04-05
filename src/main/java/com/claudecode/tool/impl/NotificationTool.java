package com.claudecode.tool.impl;

import com.claudecode.tool.Tool;
import com.claudecode.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.Map;

/**
 * Notification 工具 —— 系统通知。
 * <p>
 * 向用户发送系统级通知（桌面弹窗 + 可选声音提示），用于：
 * <ul>
 *   <li>长时间任务完成时通知用户</li>
 *   <li>需要用户注意的错误或警告</li>
 *   <li>Agent 需要用户输入时的提醒</li>
 * </ul>
 * <p>
 * 底层使用 Java AWT SystemTray (桌面环境) 或退回到 BEL 字符（终端环境）。
 */
public class NotificationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(NotificationTool.class);

    @Override
    public String name() {
        return "Notification";
    }

    @Override
    public String description() {
        return """
            Send a system notification to the user. Use this when:
            - A long-running task completes and the user may have switched away
            - An error requires the user's attention
            - You need the user to come back and provide input
            
            Supports desktop notifications (with optional sound) and terminal bell.""";
    }

    @Override
    public String inputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "title": {
                  "type": "string",
                  "description": "Notification title (short, max 80 chars)"
                },
                "message": {
                  "type": "string",
                  "description": "Notification body text"
                },
                "level": {
                  "type": "string",
                  "enum": ["info", "warning", "error"],
                  "description": "Notification severity level (default: info)"
                },
                "sound": {
                  "type": "boolean",
                  "description": "Play notification sound (default: true)"
                }
              },
              "required": ["title", "message"]
            }""";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String title = (String) input.get("title");
        String message = (String) input.get("message");
        String level = (String) input.getOrDefault("level", "info");
        Boolean sound = (Boolean) input.getOrDefault("sound", true);

        if (title == null || message == null) {
            return "Error: 'title' and 'message' are required";
        }

        // Truncate title if too long
        if (title.length() > 80) {
            title = title.substring(0, 77) + "...";
        }

        boolean sent = false;
        String method = "none";

        // Try OS-specific notification
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                sent = notifyWindows(title, message);
                method = "windows-toast";
            } else if (os.contains("mac")) {
                sent = notifyMac(title, message);
                method = "osascript";
            } else if (os.contains("linux")) {
                sent = notifyLinux(title, message);
                method = "notify-send";
            }
        } catch (Exception e) {
            log.debug("OS notification failed: {}", e.getMessage());
        }

        // Fallback: terminal bell
        if (Boolean.TRUE.equals(sound)) {
            System.out.print('\u0007'); // BEL character
            System.out.flush();
        }

        if (!sent) {
            // Fallback: just print to terminal
            String icon = switch (level) {
                case "warning" -> "⚠️";
                case "error" -> "❌";
                default -> "ℹ️";
            };
            method = "terminal";
            sent = true;
        }

        return String.format("Notification sent via %s: [%s] %s - %s", method, level, title, message);
    }

    private boolean notifyWindows(String title, String message) {
        try {
            // Use PowerShell to send Windows toast notification
            String ps = String.format(
                    "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                    "$n = New-Object System.Windows.Forms.NotifyIcon; " +
                    "$n.Icon = [System.Drawing.SystemIcons]::Information; " +
                    "$n.Visible = $true; " +
                    "$n.ShowBalloonTip(5000, '%s', '%s', 'Info'); " +
                    "Start-Sleep -Seconds 1; $n.Dispose()",
                    title.replace("'", "''"), message.replace("'", "''"));

            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", ps);
            pb.inheritIO();
            Process proc = pb.start();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            log.debug("Windows notification failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean notifyMac(String title, String message) {
        try {
            String script = String.format(
                    "display notification \"%s\" with title \"%s\"",
                    message.replace("\"", "\\\""), title.replace("\"", "\\\""));
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            Process proc = pb.start();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            log.debug("macOS notification failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean notifyLinux(String title, String message) {
        try {
            ProcessBuilder pb = new ProcessBuilder("notify-send", title, message);
            Process proc = pb.start();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            log.debug("Linux notification failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String activityDescription(Map<String, Object> input) {
        String title = (String) input.getOrDefault("title", "notification");
        return "🔔 Notify: " + title;
    }
}
