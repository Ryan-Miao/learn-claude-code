# HTTP Network Monitor — Design Spec

**Date**: 2026-04-05
**Status**: Approved
**Scope**: Chat page (`/chat`) only

## Problem

Users learning AI Agent engineering need visibility into the actual HTTP requests sent to LLM APIs. The current `HttpCaptureAdvisor` captures high-level Spring AI abstractions (model name, system prompt, thinking blocks) but **not the real wire-format HTTP data** — URL, headers, raw JSON body. Understanding the actual API contract is critical for learners.

## Solution

Add a `ClientHttpRequestInterceptor` that captures real HTTP request/response data at the `RestClient` level, then display it in a merged Monitor panel with a DevTools-style split view.

## Architecture

### Backend: HttpTrafficInterceptor

**Class**: `com.demo.learn.web.HttpTrafficInterceptor` implements `ClientHttpRequestInterceptor`

**Registration**: Custom `RestClient.Builder` bean injected into Spring AI's Anthropic/OpenAI auto-configuration. The interceptor wraps the builder with `additionalInterceptors()`.

**Capture per request**:
- Request: URL, HTTP method, all headers, raw body (JSON string)
- Response: status code, all response headers, raw body (JSON string), wall-clock duration

**Sensitivity masking**: Header values matching these patterns are replaced with `***REDACTED***` and flagged `sensitive: true`:
- `x-api-key`
- `authorization`
- `token`
- `key-*` (prefix match)
- Any header value matching regex `sk-[a-zA-Z0-9]{10,}` or `Bearer\s+\S+`

**Storage**: Per-request `ArrayList<HttpTraffic>` created in `ChatController.chat()`, passed to `HttpTrafficInterceptor` via constructor injection. Same pattern as existing `capturedRounds`.

### Backend: Data Structures

```java
// New records in HttpTrafficInterceptor or a shared file
record CapturedHeader(String name, String value, boolean sensitive) {}
record HttpTraffic(
    int round,
    String url,
    String method,
    int statusCode,
    long durationMs,
    List<CapturedHeader> requestHeaders,
    String requestBody,
    List<CapturedHeader> responseHeaders,
    String responseBody
)
```

### Backend: API Extension

`POST /api/chat` response gains a new `httpTraffic` field:

```json
{
  "text": "assistant response",
  "thinking": "...",
  "toolCalls": [{"name": "bash", "input": "...", "output": "..."}],
  "apiRoundTrips": [{"round": 1, "request": {...}, "response": {...}}],
  "httpTraffic": [
    {
      "round": 1,
      "url": "https://api.anthropic.com/v1/messages",
      "method": "POST",
      "statusCode": 200,
      "durationMs": 2341,
      "requestHeaders": [
        {"name": "content-type", "value": "application/json", "sensitive": false},
        {"name": "x-api-key", "value": "***REDACTED***", "sensitive": true}
      ],
      "requestBody": "{\"model\":\"claude-sonnet-4-20250514\",\"messages\":[...]}",
      "responseHeaders": [
        {"name": "content-type", "value": "application/json", "sensitive": false}
      ],
      "responseBody": "{\"id\":\"msg_01...\",\"content\":[...],\"usage\":{...}}"
    }
  ]
}
```

### Frontend: Merged Monitor Panel

**Existing `MonitorPanel`** is refactored to a tabbed container with two views:

| Tab | Content |
|-----|---------|
| **Overview** | Current MonitorPanel content (model, system prompt, messages, thinking, tool calls, token usage) |
| **Network** | New DevTools-style split view |

**Network tab layout** (DevTools split view):

```
┌─ Toolbar: "● Network | 3 requests · 4.9s · 3,861 tokens" ──────── [Clear] ─┐
├───────────────────┬──────────────────────────────────────────────────────────┤
│  Request List     │  Detail Panel                                     │
│ ┌───────────────┐ │  ┌─────────────────────────────────────────────────┐  │
│ │ R1 POST 2.3s  │ │  │ [Headers] [Request Body] [Response Body] [Timing] │  │
│ │ 1801 tokens   │ │  ├─────────────────────────────────────────────────┤  │
│ │ tool_use      │ │  │  Request URL: https://api.anthropic.com/v1/...   │  │
│ ├───────────────┤ │  │  Method: POST    Status: 200 OK                  │  │
│ │ R2 POST 1.8s  │ │  │  Duration: 2,341 ms                             │  │
│ │ 956 tokens    │ │  │                                                  │  │
│ │ tool_use      │ │  │  Request Headers ────────────────────────────    │  │
│ ├───────────────┤ │  │  content-type    application/json                │  │
│ │ R3 POST 0.8s  │ │  │  x-api-key       sk-***REDACTED***              │  │
│ │ 1104 tokens   │ │  │  anthropic-version  2023-06-01                   │  │
│ │ end_turn      │ │  └─────────────────────────────────────────────────┘  │
│ └───────────────┘ │                                                      │
└───────────────────┴──────────────────────────────────────────────────────────┘
```

**Left panel (36%)**: Round list with round number, method, duration, token count, finish reason badge. Selected item has blue left border + tinted background.

**Right panel (64%)**: Detail with 4 sub-tabs:
- **Headers**: Grid-aligned key-value pairs (Request Headers + Response Headers). Sensitive headers shown with red `sk-***REDACTED***` badge.
- **Request Body**: Raw JSON with syntax highlighting (keys amber, strings green, numbers blue, booleans purple, null gray).
- **Response Body**: Same JSON highlighting.
- **Timing**: Duration breakdown (DNS, connect, TLS, TTFB, download — if available, otherwise just total).

### Frontend: Files

| File | Action |
|------|--------|
| `claude-learn/.../web/HttpTrafficInterceptor.java` | **New** — `ClientHttpRequestInterceptor` capturing HTTP traffic |
| `claude-learn/.../web/RestClientConfig.java` | **New** — Custom `RestClient.Builder` bean with interceptor |
| `claude-learn/.../web/ChatController.java` | **Modify** — Create traffic list, wire interceptor, add `httpTraffic` to response |
| `web/src/lib/chat-runtime.ts` | **Modify** — Add `HttpTraffic`, `CapturedHeader` TypeScript types |
| `web/src/components/chat/monitor-panel.tsx` | **Modify** — Refactor to tabbed container (Overview / Network) |
| `web/src/components/chat/network-panel.tsx` | **New** — DevTools split view component |
| `web/src/app/globals.css` | **Modify** — Add Network panel styles (tinted neutrals, JSON syntax highlighting) |

### Visual Design

Per `.impeccable.md` design context and `frontend-design` skill:

- **Colors**: oklch tinted neutrals toward hue 260 (blue). Dark mode surfaces differentiated by lightness (11%→13%→17%), not shadows.
- **5-layer system**: blue (selected state), amber (header keys, JSON keys), red (sensitive data), emerald (status codes, HTTP methods), purple (finish reason).
- **Typography**: Monospace throughout. `font-variant-numeric: tabular-nums` for aligned number columns. 5 font sizes (9-12px) + 4 weights for hierarchy.
- **Spatial**: 4pt grid. Varied density — tighter in list (8px), looser in detail (12-16px). 36%/64% asymmetric split.
- **JSON highlighting**: Key=amber, string=emerald, number=blue, boolean=purple, null=zinc. No additional dependencies.
- **Body truncation**: Bodies > 10KB shown as first 10KB + "Show full body" expand button.

### Error Handling

- If interceptor fails to capture (e.g., body read error), record an `HttpTraffic` entry with `statusCode: -1` and the error message in `responseBody`.
- If the HTTP call itself fails (connection error), capture what we have (request data + exception message) and continue — do not block the chat flow.

### Out of Scope

- Streaming SSE chunk capture (use future enhancement)
- WebSocket traffic
- Non-Anthropic/OpenAI providers (extensible but not in initial scope)
- Persistent traffic history across requests (per-request only)
