# Chat HTTP Monitor Design Spec

## Goal

Add a "Monitor" button to the Chat UI that displays the raw AI API interaction data for each round-trip — request messages, tool calls, responses, token usage, timing. This helps users learn how AI agents communicate with LLM APIs.

## Context

- Learning project: users learn AI agent patterns through a browser
- Currently only see final text output and no visibility into the underlying API interactions
- Agentic loop may involve multiple API round-trips ( all hidden from user

## Architecture

**Backend**: Custom Spring AI `CallAdvisor` that intercepts each LLM call
**Frontend**: Expandable panel per assistant message, showing API round-trip details
**Data flow**: Advisor captures → controller returns → frontend renders

## Backend

### New class: `HttpCaptureAdvisor`

Implements Spring AI's `CallAdvisor` interface. Registered on the `ChatClient` in `ChatController` per request scope.

Captures scope: each LLM API call within a single user message turn:

```java
public class HttpCaptureAdvisor implements CallAdvisor {
    private final List<ApiRoundTrip> capturedRounds;
    
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, Chain chain) {
        long start = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);
        long duration = System.currentTimeMillis() - start;
        
        capturedRounds.add(new ApiRoundTrip(
            round, capturedRounds.size(),
            extractRequest(request),
            extractResponse(response, responseText, duration)
        ));
        
        return response;
    }
}
```

**Thread safety**: The Advisor runs在请求链中执行，意味着它 round-trip 按顺序记录。

### Data structure: `ApiRoundTrip`

```java
public record ApiRoundTrip(
    int round,
    RequestSnapshot request,
    ResponseSnapshot response
) {}

public record RequestSnapshot(
    String model,
    List<MessageSnapshot> messages,     // role + content (truncated 500 chars)
    List<String> tools,
    String systemPrompt,                  // truncated 300 chars
    int maxTokens
) {}

public record ResponseSnapshot(
    String text,
    String thinking,
    List<ToolCallSnapshot> toolCalls,
    String finishReason,
    int inputTokens,
    int outputTokens;
    long durationMs
) {}

public record MessageSnapshot(String role, String content) {}
public record ToolCallSnapshot(String name, String input) {}
```

### Sensitive data masking

- No HTTP headers captured (Advisor operates at Spring AI abstraction, API keys are
- Content matching `sk-...` pattern replaced with `***REDACTED***`
- Long text truncated to 500 chars for messages, 300 chars for system prompt

### Modified: `ChatController`

- Build `ChatClient` with `HttpCaptureAdvisor` registered
- Add `apiRoundTrips` field to response JSON
- Advisor instance shared via `ThreadLocal` or method-scoped list ( passed into `CaptureToolCallback`

### Response format change

```json
{
  "text": "AI reply",
  "thinking": "...",
  "toolCalls": [...],
  "apiRoundTrips": [
    {
      "round": 1,
      "request": {
        "model": "claude-sonnet-4-20250514",
        "messages": [{ "role": "user", "content": "list files" }],
        "tools": ["bash"],
        "systemPrompt": "You are a coding agent...",
        "maxTokens": 4096
      },
      "response": {
        "text": "",
        "thinking": "Let me list the files...",
        "toolCalls": [{ "name": "bash", "input": "ls" }],
        "finishReason": "tool_use",
        "inputTokens": 150,
        "outputTokens": 80,
        "durationMs": 1200
      }
    },
    {
      "round": 2,
      "request": { ... },
      "response": { ... }
    }
  ]
}
```

## Frontend

### Monitor button per assistant message
- Icon button (`🔍`) on each assistant message bubble, next to thinking/tool call toggles buttons
- Click to expand/collapse monitor panel below the the message

### Monitor panel content
- Summary header: total rounds, total tokens, total duration
- Per-round expandable row showing: request + response details
- Reuses existing `expandedTools` state management pattern for expanded state
- Content rendered in `<pre>` blocks with monospace formatting

## Scope

- Only applies to Chat page (`/chat`)
- Non-streaming (blocking `.call()` only)
- Data only in memory (no persistence)
- Sensitive info masked at Advisor layer

