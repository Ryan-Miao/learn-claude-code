package com.claudecode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 通知服务 —— 对应 claude-code 中 notifier 服务。
 * <p>
 * 提供跨平台桌面通知功能：
 * <ul>
 *   <li>Windows: PowerShell toast notification</li>
 *   <li>macOS: osascript display notification</li>
 *   <li>Linux: notify-send</li>
 *   <li>Fallback: terminal bell (BEL character)</li>
 * </ul>
 * <p>
 * 可配置：
 * <ul>
 *   <li>启用/禁用通知</li>
 *   <li>声音提示开关</li>
 *   <li>仅在窗口不活跃时通知</li>
 * </ul>
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private boolean enabled = true;
    private boolean soundEnabled = true;
    private boolean onlyWhenInactive = true;

    private final String os;

    public NotificationService() {
        this.os = System.getProperty("os.name", "").toLowerCase();
    }

    /**
     * 发送信息通知。
     */
    public void info(String title, String message) {
        send(title, message, "info");
    }

    /**
     * 发送警告通知。
     */
    public void warning(String title, String message) {
        send(title, message, "warning");
    }

    /**
     * 发送错误通知。
     */
    public void error(String title, String message) {
        send(title, message, "error");
    }

    /**
     * 发送通知。
     */
    public void send(String title, String message, String level) {
        if (!enabled) return;

        // 播放声音
        if (soundEnabled) {
            System.out.print('\u0007'); // BEL
            System.out.flush();
        }

        // 发送桌面通知
        try {
            if (os.contains("win")) {
                sendWindows(title, message);
            } else if (os.contains("mac")) {
                sendMac(title, message);
            } else if (os.contains("linux")) {
                sendLinux(title, message, level);
            }
        } catch (Exception e) {
            log.debug("Desktop notification failed: {}", e.getMessage());
        }
    }

    /**
     * 任务完成通知。
     */
    public void taskComplete(String taskName) {
        info("Task Complete", taskName + " has finished");
    }

    /**
     * 需要输入通知。
     */
    public void inputRequired(String reason) {
        warning("Input Required", reason);
    }

    /**
     * 错误通知。
     */
    public void errorOccurred(String toolName, String errorMessage) {
        error("Error in " + toolName, errorMessage);
    }

    // ==================== 平台通知 ====================

    private void sendWindows(String title, String message) throws IOException, InterruptedException {
        String ps = String.format(
                "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms')|Out-Null;" +
                "$n=New-Object System.Windows.Forms.NotifyIcon;" +
                "$n.Icon=[System.Drawing.SystemIcons]::Information;" +
                "$n.Visible=$true;" +
                "$n.ShowBalloonTip(3000,'%s','%s','Info');" +
                "Start-Sleep 1;$n.Dispose()",
                escape(title), escape(message));
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                .redirectErrorStream(true).start();
        // Don't block, but schedule cleanup
        p.onExit().thenRun(p::destroyForcibly);
    }

    private void sendMac(String title, String message) throws IOException {
        String script = String.format(
                "display notification \"%s\" with title \"%s\"",
                escape(message), escape(title));
        Process p = new ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true).start();
        p.onExit().thenRun(p::destroyForcibly);
    }

    private void sendLinux(String title, String message, String level) throws IOException {
        String urgency = switch (level) {
            case "error" -> "critical";
            case "warning" -> "normal";
            default -> "low";
        };
        Process p = new ProcessBuilder("notify-send", "-u", urgency, title, message)
                .redirectErrorStream(true).start();
        p.onExit().thenRun(p::destroyForcibly);
    }

    private String escape(String s) {
        return s.replace("'", "''").replace("\"", "\\\"");
    }

    // ==================== 配置 ====================

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void setSoundEnabled(boolean enabled) { this.soundEnabled = enabled; }
    public boolean isSoundEnabled() { return soundEnabled; }

    public void setOnlyWhenInactive(boolean only) { this.onlyWhenInactive = only; }
    public boolean isOnlyWhenInactive() { return onlyWhenInactive; }
}
