# HTTP Network Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture real HTTP request/response data at the RestClient level and display it in a DevTools-style split view within the existing Chat Monitor panel.

**Architecture:** A `ClientHttpRequestInterceptor` captures wire-level HTTP traffic (URL, headers, body) for each LLM API call. Data is passed per-request to the `ChatController` and returned alongside existing `apiRoundTrips`. The frontend merges a new "Network" tab into the existing `MonitorPanel`.

**Tech Stack:** Java 25 + Spring Boot 4.0 + Spring AI 2.0 (backend), Next.js 16 + React 19 + Tailwind CSS v4 (frontend)

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `claude-learn/.../web/HttpTrafficInterceptor.java` | **New** | `ClientHttpRequestInterceptor` that captures real HTTP request/response |
| `claude-learn/.../web/RestClientConfig.java` | **New** | Spring `@Configuration` providing custom `RestClient.Builder` with interceptor |
| `claude-learn/.../web/ChatController.java` | **Modify** | Create traffic list, wire interceptor, add `httpTraffic` to response JSON |
| `web/src/lib/chat-runtime.ts` | **Modify** | Add `HttpTraffic` and `CapturedHeader` TypeScript types, extend `ChatResponse` |
| `web/src/components/chat/monitor-panel.tsx` | **Modify** | Refactor to tabbed container (Overview / Network) |
| `web/src/components/chat/network-panel.tsx` | **New** | DevTools split view: left request list + right detail panel with sub-tabs |
| `web/src/app/globals.css` | **Modify** | Network panel CSS: tinted neutrals, JSON syntax highlighting |

---

### Task 1: Backend — Create `HttpTrafficInterceptor`

**Files:**
- Create: `claude-learn/src/main/java/com/demo/learn/web/HttpTrafficInterceptor.java`

- [ ] **Step 1: Write `HttpTrafficInterceptor.java`**

```java
package com.demo.learn.web;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Captures real HTTP request/response data for each LLM API call.
 * Per-request scoped: a fresh traffic list is injected by ChatController.
 */
public class HttpTrafficInterceptor implements ClientHttpRequestInterceptor {

    private final List<HttpTraffic> trafficList;
    private final int[] roundCounter = {0};

    public HttpTrafficInterceptor(List<HttpTraffic> trafficList) {
        this.trafficList = trafficList;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        int round = ++roundCounter[0];
        long start = System.currentTimeMillis();

        // Capture request data
        String url = request.getURI().toString();
        String method = request.getMethod().name();
        List<CapturedHeader> reqHeaders = captureHeaders(request.getHeaders());
        String reqBody = new String(body, StandardCharsets.UTF_8);

        // Execute and capture response
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - start;
            trafficList.add(new HttpTraffic(
                    round, url, method, -1, duration,
                    reqHeaders, truncate(reqBody, 50000),
                    List.of(), e.getMessage() != null ? e.getMessage() : "Connection error"
            ));
            throw e;
        }

        // Buffer response body so we can read it without consuming the stream
        byte[] responseBodyBytes = StreamUtils.copyToByteArray(response.getBody());
        long duration = System.currentTimeMillis() - start;

        List<CapturedHeader> respHeaders = captureHeaders(response.getHeaders());
        String respBody = new String(responseBodyBytes, StandardCharsets.UTF_8);

        trafficList.add(new HttpTraffic(
                round, url, method,
                response.getStatusCode().value(), duration,
                reqHeaders, truncate(reqBody, 50000),
                respHeaders, truncate(respBody, 50000)
        ));

        // Return a response with the buffered body so the caller can still read it
        return new BufferedClientHttpResponse(response, responseBodyBytes);
    }

    private List<CapturedHeader> captureHeaders(org.springframework.http.HttpHeaders headers) {
        List<CapturedHeader> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                boolean sensitive = isSensitiveHeader(entry.getKey(), value);
                String displayValue = sensitive ? maskValue(value) : value;
                result.add(new CapturedHeader(entry.getKey(), displayValue, sensitive));
            }
        }
        return result;
    }

    static boolean isSensitiveHeader(String name, String value) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("x-api-key") || lower.equals("authorization")
                || lower.equals("token") || lower.startsWith("key-")) {
            return true;
        }
        // Check value patterns
        if (value != null && (value.startsWith("sk-") || value.startsWith("Bearer "))) {
            return true;
        }
        return false;
    }

    static String maskValue(String value) {
        if (value == null) return "***REDACTED***";
        value = value.replaceAll("sk-[a-zA-Z0-9]{10,}", "***REDACTED***");
        value = value.replaceAll("Bearer\\s+\\S+", "Bearer ***REDACTED***");
        return value;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "... (truncated)" : text;
    }

    // --- Data records ---

    public record CapturedHeader(String name, String value, boolean sensitive) {}
    public record HttpTraffic(
            int round,
            String url,
            String method,
            int statusCode,
            long durationMs,
            List<CapturedHeader> requestHeaders,
            String requestBody,
            List<CapturedHeader> responseHeaders,
            String responseBody
    ) {}

    /**
     * Wraps a ClientHttpResponse with a buffered body so the stream can be read multiple times.
     */
    private static class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse original;
        private final byte[] body;

        BufferedClientHttpResponse(ClientHttpResponse original, byte[] body) {
            this.original = original;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return original.getHeaders();
        }

        @Override
        public org.springframework.http.HttpStatus getStatusCode() throws IOException {
            return original.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return original.getStatusText();
        }

        @Override
        public void close() {
            original.close();
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/HttpTrafficInterceptor.java
git commit -m "feat(web): add HttpTrafficInterceptor for real HTTP capture"
```

---

### Task 2: Backend — Create `RestClientConfig` to wire the interceptor

**Files:**
- Create: `claude-learn/src/main/java/com/demo/learn/web/RestClientConfig.java`

- [ ] **Step 1: Write `RestClientConfig.java`**

This configuration provides a `RestClient.Builder` bean decorated with our interceptor. Spring AI's Anthropic/OpenAI auto-configuration picks up this builder when present.

```java
package com.demo.learn.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a RestClient.Builder that captures HTTP traffic via HttpTrafficInterceptor.
 * <p>
 * The interceptor is request-scoped: each call to {@link #createTrafficList()} returns
 * a fresh list that the interceptor writes into. ChatController reads from this list
 * after the chat call completes.
 */
@Configuration
public class RestClientConfig {

    /**
     * ThreadLocal holding the active traffic list for the current request.
     * ChatController sets it before each chat call and clears it after.
     */
    private static final ThreadLocal<List<HttpTrafficInterceptor.HttpTraffic>> ACTIVE_TRAFFIC =
            new ThreadLocal<>();

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestInterceptor(new TrafficInterceptorBridge());
    }

    /**
     * Create a new traffic list and bind it to the current thread.
     * Returns the list for ChatController to read after the call.
     */
    public static List<HttpTrafficInterceptor.HttpTraffic> createTrafficList() {
        List<HttpTrafficInterceptor.HttpTraffic> list = new ArrayList<>();
        ACTIVE_TRAFFIC.set(list);
        return list;
    }

    /**
     * Clear the thread-local traffic list. Call after the chat request completes.
     */
    public static void clearTrafficList() {
        ACTIVE_TRAFFIC.remove();
    }

    /**
     * Bridge interceptor that delegates to a real HttpTrafficInterceptor
     * bound to the current thread's traffic list.
     */
    private static class TrafficInterceptorBridge implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
                                            org.springframework.http.client.ClientHttpRequestExecution execution)
                throws java.io.IOException {
            List<HttpTrafficInterceptor.HttpTraffic> trafficList = ACTIVE_TRAFFIC.get();
            if (trafficList != null) {
                return new HttpTrafficInterceptor(trafficList).intercept(request, body, execution);
            }
            // No active traffic capture — pass through
            return execution.execute(request, body);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/RestClientConfig.java
git commit -m "feat(web): add RestClientConfig with traffic capture bridge"
```

---

### Task 3: Backend — Modify `ChatController` to include `httpTraffic`

**Files:**
- Modify: `claude-learn/src/main/java/com/demo/learn/web/ChatController.java`

- [ ] **Step 1: Add traffic list creation and include in response**

In `ChatController.java`, modify the `chat()` method. Add these changes:

1. At the top of the try block (after `capturedRounds` initialization), create the traffic list:

```java
// Capture real HTTP traffic during this request
List<HttpTrafficInterceptor.HttpTraffic> httpTraffic = RestClientConfig.createTrafficList();
```

2. In the `ResponseEntity.ok(Map.of(...))` call, add `"httpTraffic"` to the map:

```java
return ResponseEntity.ok(Map.of(
        "text", textResponse.toString(),
        "thinking", thinkingResponse.toString(),
        "toolCalls", capturedCalls.stream()
                .map(c -> Map.<String, Object>of(
                        "name", c.name(),
                        "input", c.input(),
                        "output", c.output()))
                .toList(),
        "apiRoundTrips", capturedRounds.stream()
                .map(rt -> Map.<String, Object>of(
                        "round", rt.round(),
                        "request", Map.of(
                                "model", rt.request().model(),
                                "systemPrompt", rt.request().systemPrompt(),
                                "messages", rt.request().messages(),
                                "tools", rt.request().tools()
                        ),
                        "response", Map.of(
                                "text", rt.response().text(),
                                "thinking", rt.response().thinking(),
                                "finishReason", rt.response().finishReason(),
                                "inputTokens", rt.response().inputTokens(),
                                "outputTokens", rt.response().outputTokens(),
                                "durationMs", rt.response().durationMs(),
                                "toolCalls", rt.response().toolCalls().stream()
                                        .map(tc -> Map.<String, Object>of("name", tc.name(), "input", tc.input()))
                                        .toList()
                        )
                ))
                .toList(),
        "httpTraffic", httpTraffic.stream()
                .map(t -> Map.<String, Object>of(
                        "round", t.round(),
                        "url", t.url(),
                        "method", t.method(),
                        "statusCode", t.statusCode(),
                        "durationMs", t.durationMs(),
                        "requestHeaders", t.requestHeaders().stream()
                                .map(h -> Map.<String, Object>of(
                                        "name", h.name(),
                                        "value", h.value(),
                                        "sensitive", h.sensitive()))
                                .toList(),
                        "requestBody", t.requestBody(),
                        "responseHeaders", t.responseHeaders().stream()
                                .map(h -> Map.<String, Object>of(
                                        "name", h.name(),
                                        "value", h.value(),
                                        "sensitive", h.sensitive()))
                                .toList(),
                        "responseBody", t.responseBody()
                ))
                .toList()
));
```

3. In the `finally` block (add one after catch), clear the traffic list:

Change the existing `catch` to `catch+finally`:

```java
} catch (Exception e) {
    return ResponseEntity.internalServerError().body(
            Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
} finally {
    RestClientConfig.clearTrafficList();
}
```

- [ ] **Step 2: Smoke test with curl**

```bash
# Start backend
./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp

# Test in another terminal
curl -s -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"hello","agentId":"s01","sessionId":"test-123"}' | python3 -m json.tool | head -80
```

Expected: Response JSON contains `"httpTraffic"` array with at least one entry showing `url`, `method`, `requestHeaders`, `requestBody`, `responseBody`.

- [ ] **Step 3: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/ChatController.java
git commit -m "feat(web): add httpTraffic to chat API response"
```

---

### Task 4: Frontend — Add TypeScript types for `HttpTraffic`

**Files:**
- Modify: `web/src/lib/chat-runtime.ts`

- [ ] **Step 1: Add types after existing `ApiRoundTrip` interface**

Add these types at the end of the type definitions (after `ApiRoundTrip`, before `sendChatMessage`):

```typescript
export interface CapturedHeader {
  name: string;
  value: string;
  sensitive: boolean;
}

export interface HttpTraffic {
  round: number;
  url: string;
  method: string;
  statusCode: number;
  durationMs: number;
  requestHeaders: CapturedHeader[];
  requestBody: string;
  responseHeaders: CapturedHeader[];
  responseBody: string;
}
```

- [ ] **Step 2: Extend `ChatResponse` interface**

Change the existing `ChatResponse` interface from:

```typescript
export interface ChatResponse {
  text: string;
  thinking: string;
  toolCalls: ToolCall[];
  apiRoundTrips?: ApiRoundTrip[];
}
```

To:

```typescript
export interface ChatResponse {
  text: string;
  thinking: string;
  toolCalls: ToolCall[];
  apiRoundTrips?: ApiRoundTrip[];
  httpTraffic?: HttpTraffic[];
}
```

- [ ] **Step 3: Commit**

```bash
git add web/src/lib/chat-runtime.ts
git commit -m "feat(web): add HttpTraffic TypeScript types"
```

---

### Task 5: Frontend — Create `NetworkPanel` component

**Files:**
- Create: `web/src/components/chat/network-panel.tsx`

- [ ] **Step 1: Write `NetworkPanel` component**

```tsx
"use client";

import { useState } from "react";
import type { HttpTraffic } from "@/lib/chat-runtime";

interface NetworkPanelProps {
  traffic: HttpTraffic[];
}

type DetailTab = "headers" | "request" | "response";

export function NetworkPanel({ traffic }: NetworkPanelProps) {
  const [selectedIdx, setSelectedIdx] = useState(0);
  const [detailTab, setDetailTab] = useState<DetailTab>("headers");

  if (traffic.length === 0) return null;

  const selected = traffic[selectedIdx];
  const totalDuration = traffic.reduce((s, t) => s + t.durationMs, 0);
  const totalTokens = traffic.reduce(
    (s, t) => {
      // Extract token count from response body if possible
      try {
        const body = JSON.parse(t.responseBody);
        const usage = body?.usage;
        return {
          in: s.in + (usage?.input_tokens ?? 0),
          out: s.out + (usage?.output_tokens ?? 0),
        };
      } catch {
        return s;
      }
    },
    { in: 0, out: 0 }
  );

  return (
    <div
      className="nm-root"
      style={{
        fontFamily:
          "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
        fontVariantNumeric: "tabular-nums",
      }}
    >
      {/* Toolbar */}
      <div className="nm-toolbar">
        <div className="nm-toolbar-left">
          <span className="nm-status-dot" />
          <span className="nm-toolbar-title">Network</span>
          <span className="nm-toolbar-sep">│</span>
          <span className="nm-toolbar-stat">
            {traffic.length} requests · {(totalDuration / 1000).toFixed(1)}s ·{" "}
            {(totalTokens.in + totalTokens.out).toLocaleString()} tokens
          </span>
        </div>
      </div>

      {/* Split view */}
      <div className="nm-split">
        {/* Left: Request list */}
        <div className="nm-list">
          <div className="nm-list-header">
            <span></span>
            <span>Endpoint</span>
            <span className="nm-col-right">Time</span>
            <span className="nm-col-right">Status</span>
          </div>
          {traffic.map((t, i) => (
            <div
              key={i}
              className={`nm-list-item ${i === selectedIdx ? "nm-list-item-active" : ""}`}
              onClick={() => setSelectedIdx(i)}
            >
              <span
                className={`nm-round-badge ${i === selectedIdx ? "nm-round-badge-active" : ""}`}
              >
                R{t.round}
              </span>
              <div className="nm-list-item-main">
                <span className="nm-list-item-name">
                  {t.method} {new URL(t.url).pathname}
                </span>
              </div>
              <span className="nm-col-right nm-text-muted">
                {(t.durationMs / 1000).toFixed(1)}s
              </span>
              <span
                className={`nm-col-right nm-status-badge ${
                  t.statusCode >= 200 && t.statusCode < 300
                    ? "nm-status-ok"
                    : "nm-status-err"
                }`}
              >
                {t.statusCode}
              </span>
            </div>
          ))}
        </div>

        {/* Right: Detail panel */}
        <div className="nm-detail">
          {/* Detail tabs */}
          <div className="nm-detail-tabs">
            {(["headers", "request", "response"] as DetailTab[]).map((tab) => (
              <button
                key={tab}
                className={`nm-detail-tab ${detailTab === tab ? "nm-detail-tab-active" : ""}`}
                onClick={() => setDetailTab(tab)}
              >
                {tab === "headers"
                  ? "Headers"
                  : tab === "request"
                    ? "Request Body"
                    : "Response Body"}
              </button>
            ))}
          </div>

          {/* General info (always visible) */}
          <div className="nm-detail-info">
            <div className="nm-info-grid">
              <span className="nm-info-label">Request URL</span>
              <span className="nm-info-value nm-info-url">{selected.url}</span>

              <span className="nm-info-label">Method</span>
              <span>
                <span className="nm-method-badge">{selected.method}</span>
              </span>

              <span className="nm-info-label">Status</span>
              <span>
                <span
                  className={
                    selected.statusCode >= 200 && selected.statusCode < 300
                      ? "nm-status-ok"
                      : "nm-status-err"
                  }
                >
                  {selected.statusCode}
                </span>
                <span className="nm-text-muted">
                  {" "}
                  {selected.statusCode === 200
                    ? "OK"
                    : selected.statusCode === 0
                      ? "Error"
                      : ""}
                </span>
              </span>

              <span className="nm-info-label">Duration</span>
              <span className="nm-info-value">
                {selected.durationMs.toLocaleString()} ms
              </span>
            </div>
          </div>

          {/* Tab content */}
          {detailTab === "headers" && (
            <HeadersView
              requestHeaders={selected.requestHeaders}
              responseHeaders={selected.responseHeaders}
            />
          )}
          {detailTab === "request" && (
            <BodyView body={selected.requestBody} />
          )}
          {detailTab === "response" && (
            <BodyView body={selected.responseBody} />
          )}
        </div>
      </div>
    </div>
  );
}

/** Headers sub-view */
function HeadersView({
  requestHeaders,
  responseHeaders,
}: {
  requestHeaders: { name: string; value: string; sensitive: boolean }[];
  responseHeaders: { name: string; value: string; sensitive: boolean }[];
}) {
  return (
    <div className="nm-headers-scroll">
      <HeaderSection title="Request Headers" headers={requestHeaders} />
      <div className="nm-divider" />
      <HeaderSection title="Response Headers" headers={responseHeaders} />
    </div>
  );
}

function HeaderSection({
  title,
  headers,
}: {
  title: string;
  headers: { name: string; value: string; sensitive: boolean }[];
}) {
  return (
    <div className="nm-header-section">
      <div className="nm-section-label">
        {title} <span className="nm-count">{headers.length}</span>
      </div>
      <div className="nm-header-grid">
        {headers.map((h, i) => (
          <div key={i} className="nm-header-row">
            <span className="nm-header-key">{h.name}</span>
            {h.sensitive ? (
              <span className="nm-header-sensitive">{h.value}</span>
            ) : (
              <span className="nm-header-val">{h.value}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/** Body sub-view with JSON syntax highlighting */
function BodyView({ body }: { body: string }) {
  const [expanded, setExpanded] = useState(false);
  const TRUNCATE_SIZE = 10240; // 10KB

  if (!body) {
    return (
      <div className="nm-body-empty">No body</div>
    );
  }

  const isTruncated = body.length > TRUNCATE_SIZE && !expanded;
  const displayBody = isTruncated ? body.slice(0, TRUNCATE_SIZE) : body;

  // Try to pretty-print JSON
  let formatted: string;
  try {
    formatted = JSON.stringify(JSON.parse(displayBody), null, 2);
  } catch {
    formatted = displayBody;
  }

  return (
    <div className="nm-body-scroll">
      <pre className="nm-json-pre">
        <code dangerouslySetInnerHTML={{ __html: highlightJson(formatted) }} />
      </pre>
      {isTruncated && (
        <button
          className="nm-expand-btn"
          onClick={() => setExpanded(true)}
        >
          Show full body ({(body.length / 1024).toFixed(1)} KB)
        </button>
      )}
    </div>
  );
}

/** Lightweight JSON syntax highlighting — no dependencies */
function highlightJson(json: string): string {
  return json.replace(
    /("(?:\\.|[^"\\])*")\s*:/g,
    '<span class="nm-json-key">$1</span>:'
  ).replace(
    /:\s*("(?:\\.|[^"\\])*")/g,
    ': <span class="nm-json-string">$1</span>'
  ).replace(
    /:\s*(\d+(?:\.\d+)?)/g,
    ': <span class="nm-json-number">$1</span>'
  ).replace(
    /:\s*(true|false)/g,
    ': <span class="nm-json-boolean">$1</span>'
  ).replace(
    /:\s*(null)/g,
    ': <span class="nm-json-null">$1</span>'
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/components/chat/network-panel.tsx
git commit -m "feat(web): add NetworkPanel DevTools split view component"
```

---

### Task 6: Frontend — Add Network panel CSS to `globals.css`

**Files:**
- Modify: `web/src/app/globals.css`

- [ ] **Step 1: Append Network panel styles at the end of `globals.css`**

Append the following CSS block at the end of the file:

```css
/* =====================================================
   NETWORK-MONITOR: DevTools-style HTTP traffic viewer
   Tinted neutrals toward blue (hue 260), per .impeccable.md
   ===================================================== */

.nm-root {
  border-radius: 8px;
  overflow: hidden;
  background: #0d0e12;
  border: 1px solid oklch(22% 0.01 260);
}

/* -- Toolbar -- */
.nm-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: oklch(15% 0.008 260);
  border-bottom: 1px solid oklch(22% 0.01 260);
}
.nm-toolbar-left { display: flex; align-items: center; gap: 8px; }
.nm-status-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: #3b82f6;
}
.nm-toolbar-title {
  color: oklch(82% 0.02 260);
  font-size: 11px; font-weight: 600; letter-spacing: 0.02em;
}
.nm-toolbar-sep { color: oklch(35% 0.01 260); }
.nm-toolbar-stat { color: oklch(55% 0.012 260); font-size: 10px; }

/* -- Split layout -- */
.nm-split { display: flex; height: 420px; }

/* -- Left: Request list -- */
.nm-list {
  width: 36%;
  border-right: 1px solid oklch(20% 0.01 260);
  overflow-y: auto;
  background: #0d0e12;
}
.nm-list-header {
  display: grid;
  grid-template-columns: 28px 1fr 52px 52px;
  gap: 0;
  padding: 6px 10px;
  background: oklch(15% 0.008 260);
  border-bottom: 1px solid oklch(20% 0.01 260);
  position: sticky; top: 0; z-index: 1;
  font-size: 9px;
  color: oklch(42% 0.01 260);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.nm-list-item {
  display: grid;
  grid-template-columns: 28px 1fr 52px 52px;
  gap: 0;
  align-items: start;
  padding: 7px 10px;
  border-left: 2px solid transparent;
  cursor: pointer;
  transition: background 100ms ease-out;
}
.nm-list-item:hover { background: oklch(16% 0.008 260); }
.nm-list-item-active {
  background: oklch(20% 0.018 260) !important;
  border-left-color: #3b82f6;
}
.nm-round-badge {
  font-size: 10px; font-weight: 700;
  color: oklch(42% 0.01 260);
}
.nm-round-badge-active { color: #60a5fa; }
.nm-list-item-main { min-width: 0; }
.nm-list-item-name {
  font-size: 11px; color: oklch(75% 0.01 260);
  display: block; white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis;
}
.nm-col-right { text-align: right; }
.nm-text-muted { color: oklch(48% 0.01 260); font-size: 10px; }
.nm-status-badge { font-size: 10px; }
.nm-status-ok { color: #34d399; }
.nm-status-err { color: #ef4444; }

/* -- Right: Detail panel -- */
.nm-detail {
  width: 64%;
  overflow-y: auto;
  background: #0b0c10;
}
.nm-detail-tabs {
  display: flex;
  background: oklch(15% 0.008 260);
  border-bottom: 1px solid oklch(20% 0.01 260);
}
.nm-detail-tab {
  padding: 7px 14px;
  font-size: 11px;
  color: oklch(45% 0.01 260);
  background: none; border: none; cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: color 150ms ease-out, border-color 150ms ease-out;
  font-family: inherit;
}
.nm-detail-tab:hover { color: oklch(65% 0.012 260); }
.nm-detail-tab-active {
  color: oklch(88% 0.01 260) !important;
  border-bottom-color: #3b82f6 !important;
  font-weight: 500;
}

/* -- General info grid -- */
.nm-detail-info { padding: 12px 14px 16px; border-bottom: 1px solid oklch(16% 0.008 260); }
.nm-info-grid {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 5px 14px;
  font-size: 11px;
  line-height: 1.7;
}
.nm-info-label { color: oklch(42% 0.01 260); }
.nm-info-value { color: oklch(85% 0.008 260); }
.nm-info-url { color: #60a5fa; word-break: break-all; }
.nm-method-badge {
  background: oklch(28% 0.025 160);
  color: #34d399;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
}

/* -- Headers view -- */
.nm-headers-scroll { padding: 12px 14px 16px; }
.nm-header-section { margin-bottom: 12px; }
.nm-section-label {
  font-size: 9px;
  color: oklch(42% 0.01 260);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 8px;
}
.nm-count { color: oklch(35% 0.01 260); }
.nm-header-grid {
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 1px 0;
  font-size: 11px;
  line-height: 2;
}
.nm-header-key { color: #f59e0b; padding: 0 8px; }
.nm-header-val { color: oklch(62% 0.012 260); padding: 0 8px; }
.nm-header-sensitive {
  color: #ef4444;
  background: oklch(22% 0.035 25);
  padding: 0 6px;
  border-radius: 3px;
  font-size: 10px;
  border: 1px solid oklch(28% 0.025 25);
}
.nm-divider {
  margin: 12px 0;
  border-top: 1px solid oklch(16% 0.008 260);
}

/* -- Body view -- */
.nm-body-scroll { padding: 12px 14px 16px; }
.nm-body-empty {
  padding: 24px 14px;
  text-align: center;
  color: oklch(42% 0.01 260);
  font-size: 11px;
}
.nm-json-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 11px;
  line-height: 1.6;
  color: oklch(72% 0.01 260);
  max-height: 340px;
  overflow-y: auto;
}

/* JSON syntax colors */
.nm-json-key { color: #f59e0b; }
.nm-json-string { color: #34d399; }
.nm-json-number { color: #60a5fa; }
.nm-json-boolean { color: #a78bfa; }
.nm-json-null { color: oklch(48% 0.01 260); }

.nm-expand-btn {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 6px;
  background: oklch(18% 0.01 260);
  border: 1px solid oklch(24% 0.01 260);
  border-radius: 4px;
  color: oklch(58% 0.012 260);
  font-size: 10px;
  cursor: pointer;
  font-family: inherit;
  transition: background 100ms ease-out;
}
.nm-expand-btn:hover { background: oklch(22% 0.012 260); }
```

- [ ] **Step 2: Commit**

```bash
git add web/src/app/globals.css
git commit -m "feat(web): add Network Monitor panel CSS"
```

---

### Task 7: Frontend — Refactor `MonitorPanel` to tabbed container

**Files:**
- Modify: `web/src/components/chat/monitor-panel.tsx`

- [ ] **Step 1: Rewrite `monitor-panel.tsx` with Overview/Network tabs**

Replace the entire file content with:

```tsx
"use client";

import { useState } from "react";
import type { ApiRoundTrip, HttpTraffic } from "@/lib/chat-runtime";
import { NetworkPanel } from "./network-panel";

interface MonitorPanelProps {
  roundTrips: ApiRoundTrip[];
  httpTraffic?: HttpTraffic[];
}

type MonitorTab = "overview" | "network";

export function MonitorPanel({ roundTrips, httpTraffic }: MonitorPanelProps) {
  const [activeTab, setActiveTab] = useState<MonitorTab>("overview");

  if (roundTrips.length === 0) return null;

  const hasTraffic = httpTraffic && httpTraffic.length > 0;

  return (
    <div className="space-y-2">
      {/* Tab bar */}
      <div className="flex gap-1 border-b border-zinc-200 dark:border-zinc-700">
        <button
          className={`px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTab === "overview"
              ? "text-blue-600 border-b-2 border-blue-600 dark:text-blue-400 dark:border-blue-400"
              : "text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200"
          }`}
          onClick={() => setActiveTab("overview")}
        >
          Overview
        </button>
        <button
          className={`px-3 py-1.5 text-xs font-medium transition-colors ${
            activeTab === "network"
              ? "text-blue-600 border-b-2 border-blue-600 dark:text-blue-400 dark:border-blue-400"
              : "text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200"
          } ${!hasTraffic ? "opacity-40 cursor-not-allowed" : ""}`}
          onClick={() => hasTraffic && setActiveTab("network")}
          disabled={!hasTraffic}
        >
          Network {hasTraffic ? `(${httpTraffic!.length})` : ""}
        </button>
      </div>

      {/* Tab content */}
      {activeTab === "overview" && <OverviewPanel roundTrips={roundTrips} />}
      {activeTab === "network" && hasTraffic && (
        <NetworkPanel traffic={httpTraffic!} />
      )}
    </div>
  );
}

/** Existing MonitorPanel content, extracted as OverviewPanel */
function OverviewPanel({ roundTrips }: { roundTrips: ApiRoundTrip[] }) {
  return (
    <div className="space-y-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-900">
      {/* Summary */}
      <div className="flex items-center justify-between border-b border-zinc-200 pb-2 dark:border-zinc-700">
        <span className="text-xs font-medium text-zinc-600 dark:text-zinc-300">
          API Round Trips
        </span>
        <span className="font-mono text-xs text-zinc-500">
          {roundTrips.reduce((sum, rt) => sum + rt.response.inputTokens, 0)}{" "}
          in /{" "}
          {roundTrips.reduce((sum, rt) => sum + rt.response.outputTokens, 0)}{" "}
          out /{" "}
          {roundTrips.reduce((sum, rt) => sum + rt.response.durationMs, 0)}ms
        </span>
      </div>

      {/* Per-round details */}
      {roundTrips.map((rt) => (
        <div
          key={rt.round}
          className="rounded border border-zinc-200 bg-white p-2 dark:border-zinc-600 dark:bg-zinc-800"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-zinc-700 dark:text-zinc-200">
              Round {rt.round}
            </span>
            <span className="font-mono text-xs text-zinc-500">
              {rt.request.model} &middot; {rt.response.inputTokens} in /{" "}
              {rt.response.outputTokens} out &middot; {rt.response.durationMs}ms
            </span>
          </div>

          <div className="mt-2">
            <span className="text-xs font-medium text-zinc-500">Request</span>
            {rt.request.systemPrompt && (
              <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-blue-50 p-1.5 text-xs text-blue-800 dark:bg-blue-900/30 dark:text-blue-200">
                {rt.request.systemPrompt}
              </pre>
            )}
            {rt.request.messages.map((msg, i) => (
              <pre
                key={i}
                className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-blue-50 p-1.5 text-xs text-blue-800 dark:bg-blue-900/30 dark:text-blue-200"
              >
                {msg}
              </pre>
            ))}
            {rt.request.tools.length > 0 && (
              <div className="mt-1 text-xs text-zinc-500">
                Tools: {rt.request.tools.join(", ")}
              </div>
            )}
          </div>

          <div className="mt-2">
            <span className="text-xs font-medium text-zinc-500">Response</span>
            {rt.response.thinking && (
              <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-purple-50 p-1.5 text-xs text-purple-800 dark:bg-purple-900/30 dark:text-purple-200">
                {rt.response.thinking}
              </pre>
            )}
            {rt.response.text && (
              <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-green-50 p-1.5 text-xs text-green-800 dark:bg-green-900/30 dark:text-green-200">
                {rt.response.text}
              </pre>
            )}
            {rt.response.toolCalls.map((tc, i) => (
              <pre
                key={i}
                className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-amber-50 p-1.5 text-xs text-amber-800 dark:bg-amber-900/30 dark:text-amber-200"
              >
                {"\uD83D\uDD27"} {tc.name}: {tc.input}
              </pre>
            ))}
            <div className="mt-1 text-xs text-zinc-400">
              Finish: {rt.response.finishReason}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/components/chat/monitor-panel.tsx
git commit -m "feat(web): refactor MonitorPanel with Overview/Network tabs"
```

---

### Task 8: Frontend — Update `ChatPage` to pass `httpTraffic` to `MonitorPanel`

**Files:**
- Modify: `web/src/components/chat/chat-page.tsx`

- [ ] **Step 1: Import `HttpTraffic` type and add to `Message` interface**

At the top, change the import to include `HttpTraffic`:

```typescript
import {
  sendChatMessage,
  fetchAgents,
  type AgentInfo,
  type ToolCall,
  type ApiRoundTrip,
  type HttpTraffic,
} from "@/lib/chat-runtime";
```

Change the `Message` interface to add `httpTraffic`:

```typescript
interface Message {
  role: "user" | "assistant";
  text: string;
  thinking?: string;
  toolCalls?: ToolCall[];
  error?: string;
  apiRoundTrips?: ApiRoundTrip[];
  httpTraffic?: HttpTraffic[];
}
```

- [ ] **Step 2: Include `httpTraffic` in the assistant message creation**

In the `handleSend` callback, change the assistant message creation to include `httpTraffic`:

```typescript
setMessages((prev) => [
  ...prev,
  {
    role: "assistant",
    text: res.text,
    thinking: res.thinking || undefined,
    toolCalls:
      res.toolCalls && res.toolCalls.length > 0
        ? res.toolCalls
        : undefined,
    apiRoundTrips:
      res.apiRoundTrips && res.apiRoundTrips.length > 0
        ? res.apiRoundTrips
        : undefined,
    httpTraffic:
      res.httpTraffic && res.httpTraffic.length > 0
        ? res.httpTraffic
        : undefined,
  },
]);
```

- [ ] **Step 3: Pass `httpTraffic` to `MonitorPanel`**

In the Monitor rendering section, change:

```tsx
{expandedMonitoring.has(i) && (
  <MonitorPanel roundTrips={msg.apiRoundTrips!} />
)}
```

To:

```tsx
{expandedMonitoring.has(i) && (
  <MonitorPanel roundTrips={msg.apiRoundTrips!} httpTraffic={msg.httpTraffic} />
)}
```

- [ ] **Step 4: Commit**

```bash
git add web/src/components/chat/chat-page.tsx
git commit -m "feat(web): pass httpTraffic from API to MonitorPanel"
```

---

### Task 9: End-to-end integration test

**Files:** None (manual verification)

- [ ] **Step 1: Start backend and verify httpTraffic appears in API response**

```bash
./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp
```

In another terminal:

```bash
curl -s -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"list current directory","agentId":"s01","sessionId":"e2e-test-1"}' | python3 -c "
import sys, json
data = json.load(sys.stdin)
traffic = data.get('httpTraffic', [])
print(f'httpTraffic entries: {len(traffic)}')
for t in traffic:
    print(f'  R{t[\"round\"]}: {t[\"method\"]} {t[\"url\"][:50]}... -> {t[\"statusCode\"]} ({t[\"durationMs\"]}ms)')
    sensitive = [h for h in t.get('requestHeaders', []) if h.get('sensitive')]
    print(f'    Sensitive headers: {[h[\"name\"] for h in sensitive]}')
"
```

Expected output:
```
httpTraffic entries: 1 (or more if tool calls trigger additional rounds)
  R1: POST https://api.anthropic.com/v1/messages... -> 200 (xxxxms)
    Sensitive headers: ['x-api-key']
```

- [ ] **Step 2: Start frontend and verify Network tab renders**

```bash
cd web && npm run dev
```

1. Open `http://localhost:3000/zh/chat/` (or appropriate locale)
2. Send a message
3. Wait for assistant response
4. Click "Monitor (N rounds)" button
5. Verify two tabs appear: **Overview** and **Network**
6. Click **Network** tab
7. Verify: left panel shows round list, right panel shows detail with Headers/Request Body/Response Body sub-tabs
8. Click **Headers** sub-tab: verify `x-api-key` shows as `sk-***REDACTED***` with red badge
9. Click **Request Body** sub-tab: verify JSON is displayed with syntax highlighting
10. Click **Response Body** sub-tab: verify JSON is displayed with syntax highlighting

- [ ] **Step 3: Commit all if any fixes were needed**

```bash
git add -A
git commit -m "fix(web): address e2e test findings"
```
