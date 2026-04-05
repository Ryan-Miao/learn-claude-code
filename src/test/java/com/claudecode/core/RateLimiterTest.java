package com.claudecode.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * RateLimiter 单元测试。
 */
class RateLimiterTest {

    // ==================== Construction ====================

    @Test
    @DisplayName("default constructor creates valid instance")
    void defaultConstructor() {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.getRemaining("test")).isGreaterThan(0);
    }

    @Test
    @DisplayName("invalid maxRequestsPerWindow throws")
    void invalidMaxRequests() {
        assertThatThrownBy(() -> new RateLimiter(0, Duration.ofMinutes(1), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRequestsPerWindow");
    }

    @Test
    @DisplayName("null windowDuration throws")
    void nullDuration() {
        assertThatThrownBy(() -> new RateLimiter(10, null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowDuration");
    }

    @Test
    @DisplayName("zero maxConcurrent throws")
    void zeroMaxConcurrent() {
        assertThatThrownBy(() -> new RateLimiter(10, Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrent");
    }

    // ==================== tryAcquire ====================

    @Test
    @DisplayName("basic acquire succeeds within limit")
    void tryAcquire_withinLimit() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(1), 3);
        assertThat(limiter.tryAcquire("api")).isTrue();
        assertThat(limiter.tryAcquire("api")).isTrue();
        assertThat(limiter.tryAcquire("api")).isTrue();
    }

    @Test
    @DisplayName("acquire fails when window exhausted")
    void tryAcquire_windowExhausted() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1), 10);
        assertThat(limiter.tryAcquire("api")).isTrue();
        assertThat(limiter.tryAcquire("api")).isTrue();
        assertThat(limiter.tryAcquire("api")).isTrue();
        assertThat(limiter.tryAcquire("api")).isFalse(); // 4th should fail
    }

    @Test
    @DisplayName("different keys are independent")
    void tryAcquire_independentKeys() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), 10);
        assertThat(limiter.tryAcquire("api")).isTrue();
        assertThat(limiter.tryAcquire("api")).isTrue();
        assertThat(limiter.tryAcquire("api")).isFalse();
        // Different key should still work
        assertThat(limiter.tryAcquire("tool")).isTrue();
    }

    // ==================== getRemaining ====================

    @Test
    @DisplayName("remaining decreases after acquire")
    void getRemaining_decreases() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(1), 3);
        int before = limiter.getRemaining("api");
        limiter.tryAcquire("api");
        int after = limiter.getRemaining("api");
        assertThat(after).isEqualTo(before - 1);
    }

    // ==================== cooldown ====================

    @Test
    @DisplayName("cooldown blocks acquire")
    void cooldown_blocks() {
        RateLimiter limiter = new RateLimiter(100, Duration.ofMinutes(1), 10);
        limiter.setCooldown("api", Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire("api")).isFalse();
    }

    // ==================== reset ====================

    @Test
    @DisplayName("reset restores key capacity")
    void reset_restores() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), 10);
        limiter.tryAcquire("api");
        limiter.tryAcquire("api");
        assertThat(limiter.tryAcquire("api")).isFalse();

        limiter.reset("api");
        assertThat(limiter.tryAcquire("api")).isTrue();
    }

    @Test
    @DisplayName("resetAll restores all keys")
    void resetAll_restores() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1), 10);
        limiter.tryAcquire("api");
        limiter.tryAcquire("tool");
        assertThat(limiter.tryAcquire("api")).isFalse();
        assertThat(limiter.tryAcquire("tool")).isFalse();

        limiter.resetAll();
        assertThat(limiter.tryAcquire("api")).isTrue();
        assertThat(limiter.tryAcquire("tool")).isTrue();
    }

    // ==================== concurrent semaphore ====================

    @Test
    @DisplayName("acquireConcurrent respects limit")
    void acquireConcurrent() {
        RateLimiter limiter = new RateLimiter(100, Duration.ofMinutes(1), 2);
        assertThat(limiter.acquireConcurrent(1)).isTrue();
        assertThat(limiter.acquireConcurrent(1)).isTrue();
        assertThat(limiter.acquireConcurrent(0)).isFalse(); // no timeout
        limiter.releaseConcurrent();
        assertThat(limiter.acquireConcurrent(1)).isTrue();
    }
}
