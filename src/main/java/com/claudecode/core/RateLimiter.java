package com.claudecode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 速率限制器 —— 对应 claude-code 中 claudeAiLimits / policyLimits。
 * <p>
 * 本地实现（无远程策略服务），支持：
 * <ul>
 *   <li>滑动窗口请求频率限制</li>
 *   <li>并发工具执行限制</li>
 *   <li>按键（API/tool/command）独立限流</li>
 *   <li>冷却时间和指数退避</li>
 * </ul>
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /** 滑动窗口时间帧 */
    private final Duration windowDuration;

    /** 窗口内最大请求数 */
    private final int maxRequestsPerWindow;

    /** 最大并发执行数 */
    private final int maxConcurrent;

    /** 并发信号量 */
    private final Semaphore concurrentSemaphore;

    /** 每个 key 的请求时间戳记录 */
    private final ConcurrentHashMap<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    /** 冷却中的 key */
    private final ConcurrentHashMap<String, Instant> cooldowns = new ConcurrentHashMap<>();

    /** 全局请求计数（统计用） */
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger totalRejections = new AtomicInteger(0);

    /**
     * 创建速率限制器（默认配置）。
     */
    public RateLimiter() {
        this(60, Duration.ofMinutes(1), 5);
    }

    /**
     * 创建速率限制器。
     *
     * @param maxRequestsPerWindow 窗口内最大请求数
     * @param windowDuration       滑动窗口时长
     * @param maxConcurrent        最大并发执行数
     */
    public RateLimiter(int maxRequestsPerWindow, Duration windowDuration, int maxConcurrent) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowDuration = windowDuration;
        this.maxConcurrent = maxConcurrent;
        this.concurrentSemaphore = new Semaphore(maxConcurrent);
    }

    /**
     * 尝试获取执行许可（非阻塞）。
     *
     * @param key 限流键（如 "api", "tool:bash", "command:commit"）
     * @return 是否获得许可
     */
    public boolean tryAcquire(String key) {
        totalRequests.incrementAndGet();

        // 检查冷却
        Instant cooldownEnd = cooldowns.get(key);
        if (cooldownEnd != null && Instant.now().isBefore(cooldownEnd)) {
            totalRejections.incrementAndGet();
            log.debug("Rate limited (cooldown): {}", key);
            return false;
        }
        cooldowns.remove(key);

        // 检查滑动窗口
        SlidingWindow window = windows.computeIfAbsent(key,
                k -> new SlidingWindow(maxRequestsPerWindow, windowDuration));
        if (!window.tryAcquire()) {
            totalRejections.incrementAndGet();
            log.debug("Rate limited (window): {} ({}/{})", key, window.getCount(), maxRequestsPerWindow);
            return false;
        }

        return true;
    }

    /**
     * 获取并发执行许可（阻塞，带超时）。
     *
     * @param timeoutMs 超时毫秒数
     * @return 是否获得许可
     */
    public boolean acquireConcurrent(long timeoutMs) {
        try {
            return concurrentSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放并发执行许可。
     */
    public void releaseConcurrent() {
        concurrentSemaphore.release();
    }

    /**
     * 为指定 key 设置冷却时间。
     */
    public void setCooldown(String key, Duration cooldownDuration) {
        cooldowns.put(key, Instant.now().plus(cooldownDuration));
        log.debug("Cooldown set for {}: {}", key, cooldownDuration);
    }

    /**
     * 重置指定 key 的限制。
     */
    public void reset(String key) {
        windows.remove(key);
        cooldowns.remove(key);
    }

    /**
     * 重置所有限制。
     */
    public void resetAll() {
        windows.clear();
        cooldowns.clear();
        totalRequests.set(0);
        totalRejections.set(0);
    }

    /**
     * 获取剩余可用请求数。
     */
    public int getRemaining(String key) {
        SlidingWindow window = windows.get(key);
        if (window == null) return maxRequestsPerWindow;
        return Math.max(0, maxRequestsPerWindow - window.getCount());
    }

    /**
     * 获取冷却剩余时间。
     */
    public Duration getCooldownRemaining(String key) {
        Instant end = cooldowns.get(key);
        if (end == null) return Duration.ZERO;
        Duration remaining = Duration.between(Instant.now(), end);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public int getTotalRequests() { return totalRequests.get(); }
    public int getTotalRejections() { return totalRejections.get(); }
    public int getAvailableConcurrent() { return concurrentSemaphore.availablePermits(); }

    /**
     * 状态报告。
     */
    public String statusReport() {
        return String.format("RateLimiter: %d/%d requests, %d rejections, %d/%d concurrent slots",
                totalRequests.get(), maxRequestsPerWindow,
                totalRejections.get(),
                maxConcurrent - concurrentSemaphore.availablePermits(), maxConcurrent);
    }

    // ==================== 滑动窗口实现 ====================

    private static class SlidingWindow {
        private final int maxRequests;
        private final Duration windowDuration;
        private final long[] timestamps;
        private int head = 0;
        private int count = 0;

        SlidingWindow(int maxRequests, Duration windowDuration) {
            this.maxRequests = maxRequests;
            this.windowDuration = windowDuration;
            this.timestamps = new long[maxRequests + 1];
        }

        synchronized boolean tryAcquire() {
            evict();
            if (count >= maxRequests) return false;
            timestamps[(head + count) % timestamps.length] = System.currentTimeMillis();
            count++;
            return true;
        }

        synchronized int getCount() {
            evict();
            return count;
        }

        private void evict() {
            long cutoff = System.currentTimeMillis() - windowDuration.toMillis();
            while (count > 0 && timestamps[head] < cutoff) {
                head = (head + 1) % timestamps.length;
                count--;
            }
        }
    }
}
