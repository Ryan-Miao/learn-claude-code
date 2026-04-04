# Chat HTTP Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

 

**Goal:** Add a monitor button to each assistant message in the Chat UI that shows each AI API round-trip — request messages, tool calls, responses, token usage, timing.

 Sensitive data like API keys is masked.

  

**Architecture:** Backend Spring AI `CallAdvisorChain` intercepts each LLM call, data captured in a method-scoped list passed to controller, returned in response as `apiRoundTrips`. Frontend renders expandable panel per assistant message.

 

**Tech Stack:** Java 25, Spring Boot 4.0, Spring AI 2.0.0-M4, Next.js 16, React 19, TypeScript, Tailwind CSS

---

## File Structure

| File | Responsibility |
|------|---------------|
| `claude-learn/src/main/java/com/demo/learn/web/HttpCaptureAdvisor.java` | **NEW** — `CallAdvisorChain` that captures each LLM API round-trip |
| `claude-learn/src/main/java/com/demo/learn/web/ChatController.java` | **MODify** — Register advisor, add `apiRoundTrips` to response |
| `web/src/lib/chat-runtime.ts` | **Modify** — Add `ApiRoundTrips` and `ChatResponse` |
| `web/src/components/chat/monitor-panel.tsx` | **NEW** — Expandable panel showing API round-trip details |
| `web/src/components/chat/chat-page.tsx` | **Modify** — Add monitor button, integrate monitor panel |

**Files not changing:** `CaptureToolCallback.java`, `AgentRegistry.java`, `ChatSession.java`, `WebChatApp.java`, `AiConfig.java`, `agent-selector.tsx`, `next.config.ts`, `WebConfig.java` (CORS fix committed separately) |

---

## Reference
- Spec: `docs/superpowers/specs/2026-04-04-chat-http-monitor-design.md`
- Existing advisor: `claude-learn/src/main/java/com/demo/learn/core/tools/ToolCallLoggingAdvisor.java` (reference for advisor pattern)
- `ChatClient.Builder` API: `org.springframework.ai.chat.client.ChatClient.Builder` — `defaultAdvisors(Advisor... advisors)`, `defaultAdvisor(Advisor advisor)`)
- Spring AI version: `2.0.0-M4` (`CallAdvisorChain` interface: `org.springframework.ai.chat.client.advisor.CallAdvisorChain` — `adviseCall(ChatClientRequest request, CallAdvisorChain chain)` — `getName()`, `getOrder()`)

- ChatController current build: lines 55-67

- Chat-runtime.ts API URL default: `http://192.168.3.7:8080`
- chat-page.tsx Message rendering: thinking ( tool calls, text in expandable sections, thinking, tool calls collapsed by default, tool call output truncated to 200 chars
- Monitor panel uses expandable/collapse pattern with `expandedTools`/`expandedThinking` sets
 `Monitor-panel.tsx` renders the API round-trips in expandable section using the pattern

- Sensitive data: API key masked at advisor 层 ( message content scanned for `sk-...` pattern, replaced with `***REDACTED***`)

- Content truncated to 500 chars for messages, 300 chars for system prompt)

- Response text and thinking truncated to 300 chars, tool call output truncated to 2000 chars
 response text truncated to 500 chars)

---

### Task 1: Backend — Create `HttpCaptureAdvisor`

**Files:**
- Create: `claude-learn/src/main/java/com/demo/learn/web/HttpCaptureAdvisor.java`

- [ ] **Step 1: Write `HttpCaptureAdvisor` class**

```java
package com.demo.learn.web;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;

import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Interceptor that captures each LLM API round-trip for Spring AI.
 * <p>
 * Method-scoped: each request creates a fresh capturedRounds list.
 * Registered on ChatClient via defaultAdvisors().
 */
public class HttpCaptureAdvisor implements CallAdvisorChain {

    private final List<ApiRoundTrip> capturedRounds;

    private int roundCounter = 00;

    public HttpCaptureAdvisor(List<ApiRoundTrip> capturedRounds) {
        this.capturedRounds = capturedRounds;
    }

    @Override
    public String getName() {
        return "httpCaptureAdvisor";
    }

    @Override
    public int getOrder() {
        return  100;  // runs after tool call logging advisor
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        int round = roundCounter++;

        // Capture request snapshot
        String model = request.chatModel() != null ? "unknown" : request.chatModel();
        String systemPrompt = extractSystemPrompt(request.context());
        List<String> requestMessages = request.messages().stream()
                msg -> truncate(msg.getText(), 500)).collect(Collectors.toList());
        List<String> toolNames = extractToolNames(request);

 tool callbacks);

        // Call next advisor in chain
        ChatClientResponse response;
        try {
            chain.next(request);
        } catch (Exception e) {
                // On error, log round-trip but swallow
 re-throw
            capturedRounds.add(new ApiRoundTrip(
                    round,
                    new RequestSnapshot(model, systemPrompt, requestMessages, toolNames),
                    new ResponseSnapshot("", 0, e.getMessage(), 0, -1, 0, 0)));
            return;
        }

        long duration = System.currentTimeMillis() - start;

        // Capture response snapshot
        String responseText = extractResponseText(response);
        String thinking = "";
        String finishReason = "";
        int inputTokens = 0;
        int outputTokens = 0;
        List<ToolCallSnapshot> toolCalls = new ArrayList<>();

        for (Generation gen : response.getResults()) {
            AssistantMessage msg = gen.getOutput();
            // Extract text
            if (msg.getText() != null) {
                responseText = msg.getText();
            }
            // Extract thinking ( capture from metadata if available
            // Note: thinking extraction depends on provider, may vary
            Object thinkingMeta = msg.getMetadata().get("thinking");
            if (thinkingMeta instanceof String t) {
                thinking = t;
            }

            // Extract finish reason
            ChatGenerationMetadata metadata = gen.getMetadata();
            if (metadata != null) {
                finishReason = metadata.getFinishReason() != null ? "UNKNOWN";
            }

            // Extract token usage
            Usage = metadata.getUsage();
            if (usage != null) {
                inputTokens = usage.getPromptTokensCount();
                outputTokens = usage.getCompletionTokensCount();
            }

            // Extract tool calls from response
            toolCalls = extractToolCalls(msg, response);
        }

        capturedRounds.add(new ApiRoundTrip(
                round,
                new RequestSnapshot(model, systemPrompt, requestMessages, toolNames),
                new ResponseSnapshot(
                    truncate(responseText, 500),
                    thinking,
                    finishReason,
                    inputTokens,
                    outputTokens,
                    toolCalls),
                duration
            )
        );

        return response;
    }

    // --- Helper methods ---

    private String extractSystemPrompt(Object context) {
        // context may contain system prompt as ChatModel options or ChatClientRequest
        // Try to get it from the chatOptions or the advice params
        try {
            var chatOptions = request.getClass().getMethod("getChatOptions");
            if (chatOptions != null) {
                var system = chatOptions.getSystemMessage();
                if (system instanceof SystemMessage) {
                    return truncate(system.getText(), 300);
            }
        } catch (Exception ignored) {
            // Reflection fallback — try other approaches
        return "";
    }

    private String extractModel(org.springframework.ai.chat.model.ChatModel chatModel) {
        // ChatModel is a MapModel or DefaultModel, etc.
        // Try to get the model name
        try {
            var method = chatModel.getClass().getMethod("getModel");
            if (method != null) {
                var model = method.invoke(chatModel);
                return model != null ? model.toString() : "unknown";
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private List<String> extractMessages(List<Message> messages) {
        List<String> result = new ArrayList<>();
        for (Message msg : messages) {
            String text = "";
            if (msg instanceof UserMessage) {
                text = ((UserMessage) msg).getText();
            } else if (msg instanceof AssistantMessage) {
                text = ((AssistantMessage) msg).getText();
            } else if (msg instanceof SystemMessage) {
                text = ((SystemMessage) msg).getText();
            }
            result.add(truncate(text != null ? text : "", 500));
        }
        return result;
    }

    private List<String> extractToolNames(List<ToolCallback> callbacks) {
        if (callbacks == null) return List.of();
        List<String> names = new ArrayList<>();
        for (ToolCallback cb : callbacks) {
            names.add(cb.getToolDefinition().name());
        }
        return names;
    }

    private List<ToolCallSnapshot> extractToolCalls(AssistantMessage msg, ChatResponse chatResponse) {
        // Tool calls come from the assistant message's toolCalls
        List<ToolCallSnapshot> result = new ArrayList<>();
        try {
            var toolCalls = msg.getToolCalls();
            if (toolCalls != null) {
                for (var tc : toolCalls) {
                    result.add(new ToolCallSnapshot(
                        tc.name(),
                        truncate(tc.arguments() != null ? tc.arguments() : "", 2000)
                    ));
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    // Also check tool calls in ChatResponse metadata
    // Spring AI may return tool call info in the generation metadata
    try {
        if (chatResponse != null && chatResponse.getResults() != null) {
            for (Generation gen : chatResponse.getResults()) {
                var toolCalls = gen.getOutput().getToolCalls();
                if (toolCalls != null) {
                    for (var tc : toolCalls) {
                        result.add(new ToolCallSnapshot(
                            tc.name(),
                            truncate(tc.arguments() != null ? tc.arguments() : "", 2000)
                        ));
                    }
                }
            }
        }
    } catch (Exception ignored) {}

    return result;
    }

    private String maskApiKey(String text) {
        if (text == null) return "";
        return text.replaceAll("sk-[a-zA-Z0-]{20,}", "***REDACTED***");
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "... (truncated)" : text;
    }

    // --- Data records ---

    public record RequestSnapshot(
        String model,
        String systemPrompt,
        List<String> messages,
        List<String> tools
    ) {}

    public record ResponseSnapshot(
        String text,
        String thinking,
        String finishReason,
        int inputTokens,
        int outputTokens,
        List<ToolCallSnapshot> toolCalls,
        long durationMs
    ) {}

    public record ToolCallSnapshot(
        String name,
        String input
    ) {}

    public record ApiRoundTrip(
        int round,
        RequestSnapshot request,
        ResponseSnapshot response
    ) {}
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :claude-learn:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/HttpCaptureAdvisor.java
git commit -m "feat(web): add HttpCaptureAdvisor for API round-trip capture"
```

---

### Task 2: Backend — Modify `ChatController` to integrate advisor

**Files:**
- Modify: `claude-learn/src/main/java/com/demo/learn/web/ChatController.java` (lines 44-66)

- [ ] **Step 1: Register `HttpCaptureAdvisor` in ChatClient build**

In `ChatController.java`,chat()` method, replace the ChatClient build (lines 55-58):

```java
// Before:
ChatClient chatClient = ChatClient.builder(aiConfig.get())
    .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
    .build();

// After:
List<HttpCaptureAdvisor.ApiRoundTrip> capturedRounds = new ArrayList<>();
ChatClient chatClient = ChatClient.builder(aiConfig.get())
    .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
    .defaultAdvisors(new HttpCaptureAdvisor(capturedRounds))
    .build();
```

Add the import at top: `import java.util.ArrayList;`

- [ ] **Step 2: Add `apiRoundTrips` to response JSON**

Replace the return block (around line 47-56) to include `apiRoundTrips`:

```java
// In the success return, add:
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
                            .map(tc -> Map.of("name", tc.name(), "input", tc.input()))
                            .toList()
                )
            ))
            .toList()
));
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :claude-learn:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual smoke test**

```bash
# Start backend
./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp

# In another terminal:
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"hello","agentId":"s01","sessionId":"test-monitor"}' | python3 -m json.tool
```

Expected: Response includes `apiRoundTrips` array with at least one round-trip entry containing request/response data.

- [ ] **Step 5: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/ChatController.java
git commit -m "feat(web): integrate HttpCaptureAdvisor into ChatController"
```

---

### Task 3: Frontend — Update types and add monitor panel

**Files:**
- Modify: `web/src/lib/chat-runtime.ts` (add types)
- Create: `web/src/components/chat/monitor-panel.tsx` (monitor UI)
- Modify: `web/src/components/chat/chat-page.tsx` (add monitor button)

- [ ] **Step 1: Add `ApiRoundTrip` types to `chat-runtime.ts`**

Add after the existing `AgentInfo` interface:

```typescript
export interface ApiRoundTrip {
  round: number;
  request: {
    model: string;
    systemPrompt: string;
    messages: string[];
    tools: string[];
  };
  response: {
    text: string;
    thinking: string;
    finishReason: string;
    inputTokens: number;
    outputTokens: number;
    durationMs: number;
    toolCalls: { name: string; input: string }[];
  };
}
```

Also update `ChatResponse` interface (if it exists) — add `apiRoundTrips?: ApiRoundTrip[]` field.

Note: `chat-runtime.ts` currently doesn't have a `ChatResponse` interface. The `sendChatMessage` function returns `ChatResponse` which is defined inline. Add the types above and update `ChatResponse` to include `apiRoundTrips`:

```typescript
export interface ChatResponse {
  text: string;
  thinking: string;
  toolCalls: ToolCall[];
  apiRoundTrips?: ApiRoundTrip[];
}
```

- [ ] **Step 2: Create `MonitorPanel` component**

Create `web/src/components/chat/monitor-panel.tsx`:

```tsx
"use client";

import type { ApiRoundTrip } from "@/lib/chat-runtime";

interface MonitorPanelProps {
  roundTrips: ApiRoundTrip[];
}

export function MonitorPanel({ roundTrips }: MonitorPanelProps) {
  if (roundTrips.length === 0) return null;

  return (
    <div className="space-y-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-900">
      {/* Summary */}
      <div className="flex items-center justify-between border-b border-zinc-200 pb-2 dark:border-zinc-700">
        <span className="text-xs font-medium text-zinc-600 dark:text-zinc-300">
          API Round Trips
        </span>
        <span className="font-mono text-xs text-zinc-500">
          {roundTrips.reduce((sum, rt) => sum + rt.response.inputTokens, 0)} in /{" "}
          {roundTrips.reduce((sum, rt) => sum + rt.response.outputTokens, 0)} out /{" "}
          {roundTrips.reduce((sum, rt) => sum + rt.response.durationMs, 0)}ms
        </span>
      </div>

      {/* Per-round details */}
      {roundTrips.map((rt) => (
        <div key={rt.round} className="rounded border border-zinc-200 bg-white p-2 dark:border-zinc-600 dark:bg-zinc-800">
          {/* Round header */}
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-zinc-700 dark:text-zinc-200">
              Round {rt.round}
            </span>
            <span className="font-mono text-xs text-zinc-500">
              {rt.request.model} &middot; {rt.response.inputTokens} in /{" "}
              {rt.response.outputTokens} out &middot; {rt.response.durationMs}ms
            </span>
          </div>

          {/* Request */}
          <div className="mt-2">
            <span className="text-xs font-medium text-zinc-500">Request</span>
            {rt.request.systemPrompt && (
              <pre className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-blue-50 p-1.5 text-xs text-blue-800 dark:bg-blue-900/30 dark:text-blue-200">
                {rt.request.systemPrompt}
              </pre>
            )}
            {rt.request.messages.map((msg, i) => (
              <pre key={i} className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-blue-50 p-1.5 text-xs text-blue-800 dark:bg-blue-900/30 dark:text-blue-200">
                {msg}
              </pre>
            ))}
            {rt.request.tools.length > 0 && (
              <div className="mt-1 text-xs text-zinc-500">
                Tools: {rt.request.tools.join(", ")}
              </div>
            )}
          </div>

          {/* Response */}
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
              <pre key={i} className="mt-1 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-amber-50 p-1.5 text-xs text-amber-800 dark:bg-amber-900/30 dark:text-amber-200">
                🔧 {tc.name}: {tc.input}
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

- [ ] **Step 3: Add monitor button to `chat-page.tsx`**

In `chat-page.tsx`,:

1. Import `MonitorPanel` and `ApiRoundTrip`:
```tsx
import { MonitorPanel } from "./monitor-panel";
import type { ApiRoundTrip } from "@/lib/chat-runtime";
```

2. Add to `Message` interface:
```tsx
interface Message {
  role: "user" | "assistant";
  text: string;
  thinking?: string;
  toolCalls?: ToolCall[];
  error?: string;
  apiRoundTrips?: ApiRoundTrip[];  // ADD this field
}
```

3. Add `expandedMonitoring` state (alongside existing expandedTools/expandedThinking):
```tsx
const [expandedMonitoring, setExpandedMonitoring] = useState<Set<number>>(new Set());
```

4. Add monitor button after each assistant message's tool calls block (around line 184). After tool calls and before text):
```tsx
{/* Monitor button */}
{msg.apiRoundTrips && msg.apiRoundTrips.length > 0 && (
  <div className="mt-1">
    <button
      onClick={() => toggleMonitoring(i)}
      className="flex items-center gap-1 text-xs text-zinc-400 hover:text-zinc-600 dark:text-zinc-500 dark:hover:text-zinc-300"
    >
      <svg className="h-4 w-4" viewBox="0 0 0 6" fill="none" xmlns="http://www.w3.org/2009/xlink" stroke-linejoin="round-stroke-linejoin=" d="M13.8-2.82    />
    <span className="text-xs">Monitor</span>
    </button>
    {expandedMonitoring.has(i) && (
      <MonitorPanel roundTrips={msg.apiRoundTrips!} />
    )}
  </div>
)}
```

5. Update `handleSend` to include `apiRoundTrips` in the assistant message:
```tsx
// In the success handler, add apiRoundTrips:
{
  role: "assistant",
  text: res.text,
  thinking: res.thinking || undefined,
  toolCalls: res.toolCalls && res.toolCalls.length > 0 ? res.toolCalls : undefined,
  apiRoundTrips: res.apiRoundTrips || res.apiRoundTrips.length > 0 ? res.apiRoundTrips : undefined,
}
```

- [ ] **Step 4: Verify frontend builds**

Run: `cd web && npm run build`
Expected: Build succeeds, chat pages exported

 static HTML

- [ ] **Step 5: Commit**

```bash
git add web/src/lib/chat-runtime.ts web/src/components/chat/monitor-panel.tsx web/src/components/chat/chat-page.tsx
git commit -m "feat(web): add HTTP monitor panel to chat UI"
```

---

### Task 4: Commit existing changes and verify end-to-end

**Files:**
- Modify: `claude-learn/src/main/java/com/demo/learn/web/WebConfig.java` (CORS fix, already committed)
- Modify: `web/src/lib/chat-runtime.ts` (API URL fix, already committed)
- Commit: `claude-learn/src/main/java/com/demo/learn/core/ApiException.java` (new, already committed)
- Commit: `docs/superpowers/specs/2026-04-04-chat-http-monitor-design.md` (new)
 just committed)
- Commit: `docs/superpowers/plans/2026-04-04-chat-http-monitor.md` (this file)

- Modify: `claude-learn/src/main/java/com/demo/learn/core/AgentRunner.java` (refactored to ApiException, already committed)
- Modify: `claude-learn/build.gradle.kts` (ANTHROPIC_LOG env var, already committed)
 - Modify: `claude-learn/src/main/resources/application-local.yml` (removed logging lines, already committed)
- Modify: `claude-learn/src/main/resources/application-local.yml.example` (removed logging lines, already committed)
- Modify: `claude-learn/src/main/resources/application.yml` (added comment about ANTHROPIC_LOG, already committed)
- Delete: `claude-learn/src/main/java/com/demo/learn/core/config/ErrorHandlingInterceptor.java` (deleted, already committed)
 - Delete: `claude-learn/src/main/java/com/demo/learn/core/config/HttpLoggingInterceptor.java` (deleted, already committed)
 - Delete: `claude-learn/src/main/java/com/demo/learn/core/config/RestClientConfig.java` (deleted, already committed)
- Modify: `web/src/components/layout/header.tsx` (added chat nav, already committed)
- Modify: `web/src/i18n/messages/en.json` (added chat nav item, already committed)
- Modify: `web/src/i18n/messages/zh.json` (added chat nav item, already committed)
- modify: `web/src/i18n/messages/ja.json` (added chat nav item, already committed)

- [ ] **Step 1: Stage and commit all uncommitted changes**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/WebConfig.java \
       claude-learn/src/main/java/com/demo/learn/core/ApiException.java \
       claude-learn/src/main/java/com/demo/learn/core/AgentRunner.java \
       claude-learn/build.gradle.kts \
       claude-learn/src/main/resources/application-local.yml \
       claude-learn/src/main/resources/application-local.yml.example \
       claude-learn/src/main/resources/application.yml \
       web/src/components/layout/header.tsx \
       web/src/i18n/messages/en.json \
       web/src/i18n/messages/zh.json \
       web/src/i18n/messages/ja.json \
       web/src/lib/chat-runtime.ts \
       web/src/components/chat/chat-page.tsx
git add claude-learn/src/main/java/com/demo/learn/core/config/ErrorHandlingInterceptor.java \
       claude-learn/src/main/java/com/demo/learn/core/config/HttpLoggingInterceptor.java \
       claude-learn/src/main/java/com/demo/learn/core/config/RestClientConfig.java
git commit -m "feat(web): chat UI improvements - CORS fix, header nav, ApiException refactor, API URL"
```

Note: This uses `git rm` for deleted files.

- [ ] **Step 2: Verify build still works**

```bash
./gradlew :claude-learn:compileJava && cd web && npm run build
```

Expected: Both backend and frontend build successfully.

- [ ] **Step 3: Done** ✓

---

### Task 5: Integration test — verify full flow

**Files:**
- No new files

- [ ] **Step 1: Kill any running backend/frontend processes**

- [ ] **Step 2: Start backend**

```bash
./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp
```

Expected: Spring Boot starts on port 8080

- [ ] **Step 3: Start frontend**

```bash
cd web && npx next dev -p 3000
```

- [ ] **Step 4: Test in browser**

Open: `http://192.168.3.7:3000/learn-claude-code/zh/chat/`

1. Send "list files" message
2. Verify monitor button (🔍) appears in assistant message
3. Click monitor button to see API round-trip details
4. Verify round-trip data includes model, tokens, duration, messages
5. Send another message and verify multi-round trips if agent uses tools

Expected: Monitor panel shows round-trips with request/response data, multiple rounds visible when tool use occurs

- [ ] **Step 5: Commit any integration fixes**

```bash
git add -u
git commit -m "fix(web): integration fixes for HTTP monitor"
```
