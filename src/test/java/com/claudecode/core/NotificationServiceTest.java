package com.claudecode.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * NotificationService 单元测试。
 */
class NotificationServiceTest {

    // ==================== Configuration ====================

    @Test
    @DisplayName("enabled by default")
    void enabledByDefault() {
        NotificationService service = new NotificationService();
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("sound enabled by default")
    void soundEnabledByDefault() {
        NotificationService service = new NotificationService();
        assertThat(service.isSoundEnabled()).isTrue();
    }

    @Test
    @DisplayName("only when inactive by default")
    void onlyWhenInactiveByDefault() {
        NotificationService service = new NotificationService();
        assertThat(service.isOnlyWhenInactive()).isTrue();
    }

    @Test
    @DisplayName("setEnabled toggles state")
    void setEnabled() {
        NotificationService service = new NotificationService();
        service.setEnabled(false);
        assertThat(service.isEnabled()).isFalse();
        service.setEnabled(true);
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("setSoundEnabled toggles state")
    void setSoundEnabled() {
        NotificationService service = new NotificationService();
        service.setSoundEnabled(false);
        assertThat(service.isSoundEnabled()).isFalse();
    }

    // ==================== Disabled notification ====================

    @Test
    @DisplayName("disabled service does not throw")
    void disabled_noThrow() {
        NotificationService service = new NotificationService();
        service.setEnabled(false);
        service.setSoundEnabled(false);
        // Should not throw
        assertThatCode(() -> service.info("Test", "message")).doesNotThrowAnyException();
        assertThatCode(() -> service.warning("Test", "warning")).doesNotThrowAnyException();
        assertThatCode(() -> service.error("Test", "error")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("convenience methods do not throw")
    void convenienceMethods_noThrow() {
        NotificationService service = new NotificationService();
        service.setEnabled(false);
        service.setSoundEnabled(false);
        assertThatCode(() -> service.taskComplete("build")).doesNotThrowAnyException();
        assertThatCode(() -> service.inputRequired("approval")).doesNotThrowAnyException();
        assertThatCode(() -> service.errorOccurred("bash", "exit 1")).doesNotThrowAnyException();
    }
}
