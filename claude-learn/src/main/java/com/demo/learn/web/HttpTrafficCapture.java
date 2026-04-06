package com.demo.learn.web;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HttpTrafficCapture {

    private static final ThreadLocal<AtomicInteger> ROUND_COUNTER =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));
    private static final ThreadLocal<List<CapturedRound>> ROUNDS =
            ThreadLocal.withInitial(ArrayList::new);

    public void startCapture() {
        ROUND_COUNTER.set(new AtomicInteger(0));
        ROUNDS.set(new ArrayList<>());
    }

    public void capture(CapturedRound round) {
        ROUNDS.get().add(round);
    }

    public int nextRound() {
        return ROUND_COUNTER.get().incrementAndGet();
    }

    public List<CapturedRound> getRounds() {
        return List.copyOf(ROUNDS.get());
    }

    public void clear() {
        ROUND_COUNTER.remove();
        ROUNDS.remove();
    }

    public record CapturedRound(
            int round,
            String url,
            String method,
            Map<String, String> requestHeaders,
            String requestBody,
            int statusCode,
            Map<String, String> responseHeaders,
            String responseBody,
            long durationMs
    ) {}
}
