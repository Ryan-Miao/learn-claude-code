# Chat Web UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an interactive chat web UI that lets users learn AI agent patterns through a browser, showing thinking blocks, tool calls, and AI responses.

**Architecture:** Spring Boot REST API backend (port 8080) + Next.js frontend page using assistant-ui React components. The backend exposes `/api/chat` and `/api/agents` endpoints. The frontend is a client-side rendered page in the existing Next.js static-export site.

**Tech Stack:** Java 25, Spring Boot 4.0, Spring AI 2.0 (Anthropic SDK), Next.js 16, React 19, Tailwind CSS, @assistant-ui/react

**Spec:** `docs/superpowers/specs/2026-04-04-chat-web-ui-design.md`

---

## File Structure

### Backend (new files)

| File | Responsibility |
|------|---------------|
| `claude-learn/src/main/java/com/demo/learn/web/WebChatApp.java` | Spring Boot entry point (starts Tomcat) |
| `claude-learn/src/main/java/com/demo/learn/web/ChatController.java` | REST endpoints: `/api/agents`, `/api/chat` |
| `claude-learn/src/main/java/com/demo/learn/web/AgentRegistry.java` | Agent definitions (system prompt + tools) |
| `claude-learn/src/main/java/com/demo/learn/web/ChatSession.java` | In-memory session: conversation history + tool call capture |
| `claude-learn/src/main/java/com/demo/learn/web/CaptureToolCallback.java` | ToolCallback wrapper that captures input/output |
| `claude-learn/src/main/java/com/demo/learn/web/WebConfig.java` | CORS config for localhost:3000 |

### Frontend (new files)

| File | Responsibility |
|------|---------------|
| `web/src/app/[locale]/(learn)/chat/page.tsx` | Chat page (client component) |
| `web/src/lib/chat-runtime.ts` | assistant-ui runtime adapter (fetch → message format) |
| `web/src/components/chat/agent-selector.tsx` | Agent dropdown selector |
| `web/src/components/chat/chat-page.tsx` | Main chat UI with assistant-ui components |

### No modifications to existing files

Existing S01-S12 CLI entry points are untouched.

---

### Task 1: Backend — WebChatApp entry point + CORS config

**Files:**
- Create: `claude-learn/src/main/java/com/demo/learn/web/WebChatApp.java`
- Create: `claude-learn/src/main/java/com/demo/learn/web/WebConfig.java`

- [ ] **Step 1: Create WebChatApp.java**

```java
package com.demo.learn.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web 聊天入口。启动 Tomcat 提供 REST API。
 * 与 S01-S12 的 CLI 入口独立，不影响现有命令行交互。
 */
@SpringBootApplication(scanBasePackages = {"com.demo.learn.core", "com.demo.learn.web"})
public class WebChatApp {
    public static void main(String[] args) {
        SpringApplication.run(WebChatApp.class, args);
    }
}
```

- [ ] **Step 2: Create WebConfig.java with CORS**

```java
package com.demo.learn.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
```

- [ ] **Step 3: Verify backend starts**

Run: `./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp`
Expected: Spring Boot starts on port 8080, no errors. `curl http://localhost:8080/api/agents` returns 404 (endpoints not yet created).

- [ ] **Step 4: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/WebChatApp.java claude-learn/src/main/java/com/demo/learn/web/WebConfig.java
git commit -m "feat(web): add WebChatApp entry point with CORS config"
```

---

### Task 2: Backend — CaptureToolCallback

**Files:**
- Create: `claude-learn/src/main/java/com/demo/learn/web/CaptureToolCallback.java`

- [ ] **Step 1: Create CaptureToolCallback.java**

This wraps an existing `ToolCallback` and records input/output into a shared list. Spring AI 2.0's `ToolCallback` interface has `call(String toolInput)` which is invoked for each tool execution inside ChatClient's agentic loop.

```java
package com.demo.learn.web;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

/**
 * Decorates a ToolCallback to capture input/output for web response.
 */
public class CaptureToolCallback implements ToolCallback {

    private static final int MAX_OUTPUT_LENGTH = 2000;

    private final ToolCallback delegate;
    private final List<CapturedToolCall> capturedCalls;

    public CaptureToolCallback(ToolCallback delegate, List<CapturedToolCall> capturedCalls) {
        this.delegate = delegate;
        this.capturedCalls = capturedCalls;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String result = delegate.call(toolInput);
        capturedCalls.add(new CapturedToolCall(
                getToolDefinition().name(),
                truncate(toolInput, MAX_OUTPUT_LENGTH),
                truncate(result, MAX_OUTPUT_LENGTH)
        ));
        return result;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        capturedCalls.add(new CapturedToolCall(
                getToolDefinition().name(),
                truncate(toolInput, MAX_OUTPUT_LENGTH),
                truncate(result, MAX_OUTPUT_LENGTH)
        ));
        return result;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "... (truncated)" : text;
    }

    /**
     * Record of a single tool call for the web response.
     */
    public record CapturedToolCall(String name, String input, String output) {}
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :claude-learn:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/CaptureToolCallback.java
git commit -m "feat(web): add CaptureToolCallback to record tool input/output"
```

---

### Task 3: Backend — AgentRegistry + ChatSession

**Files:**
- Create: `claude-learn/src/main/java/com/demo/learn/web/AgentRegistry.java`
- Create: `claude-learn/src/main/java/com/demo/learn/web/ChatSession.java`

- [ ] **Step 1: Create ChatSession.java**

Holds in-memory conversation history and tool call capture list per session.

```java
package com.demo.learn.web;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory chat sessions.
 * Each session holds conversation history. Refreshing the page creates a new session.
 */
public class ChatSession {

    private final ConcurrentHashMap<String, List<Message>> sessions = new ConcurrentHashMap<>();

    /**
     * Returns a snapshot copy of the conversation history.
     * Safe for iteration; modifications won't affect stored history.
     */
    public List<Message> getHistory(String sessionId) {
        List<Message> history = sessions.get(sessionId);
        return history != null ? List.copyOf(history) : List.of();
    }

    public void addUserMessage(String sessionId, String content) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(new UserMessage(content));
    }

    public void addAssistantMessage(String sessionId, AssistantMessage message) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    public void addToolResponseMessage(String sessionId, ToolResponseMessage message) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    public void setSystemPrompt(String sessionId, String systemPrompt) {
        List<Message> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        // Remove existing system message if present
        history.removeIf(m -> m instanceof SystemMessage);
        history.add(0, new SystemMessage(systemPrompt));
    }

    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
```

- [ ] **Step 2: Create AgentRegistry.java**

Registers agent definitions. Uses `org.springframework.ai.support.ToolCallbacks.from()` to convert `@Tool` annotated objects into `ToolCallback` instances, then wraps them with `CaptureToolCallback`.

```java
package com.demo.learn.web;

import com.demo.learn.core.tools.BashTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available agents with their system prompts and tools.
 */
public class AgentRegistry {

    public record AgentDef(String agentId, String name, String systemPrompt, boolean enabled) {}

    private static final Map<String, AgentDef> AGENTS = new ConcurrentHashMap<>();

    static {
        AGENTS.put("s01", new AgentDef("s01", "S01 Agent Loop",
                "You are a coding agent at " + System.getProperty("user.dir")
                        + ". Use bash to solve tasks. Act, don't explain.",
                true));
    }

    public static List<AgentDef> listAgents() {
        return new ArrayList<>(AGENTS.values());
    }

    public static AgentDef getAgent(String agentId) {
        return AGENTS.get(agentId);
    }

    /**
     * Build tool callbacks for an agent, wrapped with CaptureToolCallback
     * to record input/output into the provided list.
     */
    public static List<ToolCallback> buildToolCallbacks(String agentId, List<CaptureToolCallback.CapturedToolCall> capturedCalls) {
        AgentDef agent = AGENTS.get(agentId);
        if (agent == null) return List.of();

        List<ToolCallback> callbacks = new ArrayList<>();
        // S01 has only BashTool
        if ("s01".equals(agentId)) {
            ToolCallback[] bashCallbacks = ToolCallbacks.from(new BashTool());
            Arrays.stream(bashCallbacks)
                    .forEach(cb -> callbacks.add(new CaptureToolCallback(cb, capturedCalls)));
        }
        return callbacks;
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :claude-learn:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/AgentRegistry.java claude-learn/src/main/java/com/demo/learn/web/ChatSession.java
git commit -m "feat(web): add AgentRegistry and ChatSession"
```

---

### Task 4: Backend — ChatController

**Files:**
- Create: `claude-learn/src/main/java/com/demo/learn/web/ChatController.java`

- [ ] **Step 1: Create ChatController.java**

The main REST controller. For each chat request, it:
1. Gets/creates conversation history from ChatSession
2. Builds a ChatClient with CaptureToolCallback-wrapped tools
3. Calls the AI model with full conversation history
4. Extracts thinking blocks and text from the response
5. Returns structured JSON

```java
package com.demo.learn.web;

import com.demo.learn.core.config.AiConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AiConfig aiConfig;
    private final ChatSession chatSession = new ChatSession();

    public ChatController(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    @GetMapping("/agents")
    public List<Map<String, Object>> listAgents() {
        return AgentRegistry.listAgents().stream()
                .map(a -> Map.<String, Object>of(
                        "agentId", a.agentId(),
                        "name", a.name(),
                        "enabled", a.enabled()))
                .toList();
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        AgentRegistry.AgentDef agent = AgentRegistry.getAgent(request.agentId());
        if (agent == null || !agent.enabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown or disabled agent: " + request.agentId()));
        }

        // Initialize session with system prompt if new
        chatSession.setSystemPrompt(request.sessionId(), agent.systemPrompt());
        chatSession.addUserMessage(request.sessionId(), request.message());

        // Capture tool calls during this request
        List<CaptureToolCallback.CapturedToolCall> capturedCalls = new ArrayList<>();
        List<org.springframework.ai.tool.ToolCallback> toolCallbacks =
                AgentRegistry.buildToolCallbacks(request.agentId(), capturedCalls);

        try {
            // Build ChatClient once per request with captured tool callbacks
            ChatClient chatClient = ChatClient.builder(aiConfig.get())
                    .defaultToolCallbacks(toolCallbacks.toArray(new org.springframework.ai.tool.ToolCallback[0]))
                    .build();

            // Build conversation history: system prompt + past messages + current user message
            // chatSession already has system prompt and user message added above
            List<org.springframework.ai.chat.messages.Message> history =
                    new ArrayList<>(chatSession.getHistory(request.sessionId()));

            // Call with full conversation history
            ChatResponse response = chatClient.prompt()
                    .messages(history)
                    .call()
                    .chatResponse();

            // Extract thinking and text
            StringBuilder textResponse = new StringBuilder();
            StringBuilder thinkingResponse = new StringBuilder();

            for (Generation gen : response.getResults()) {
                AssistantMessage msg = gen.getOutput();
                if (msg.getMetadata().containsKey("signature")
                        || msg.getMetadata().containsKey("thinking")) {
                    if (msg.getText() != null && !msg.getText().isBlank()) {
                        if (thinkingResponse.length() > 0) thinkingResponse.append("\n");
                        thinkingResponse.append(msg.getText());
                    }
                } else if (msg.getText() != null && !msg.getText().isBlank()) {
                    if (textResponse.length() > 0) textResponse.append("\n");
                    textResponse.append(msg.getText());
                }
            }

            // Update session history with assistant response
            if (!response.getResults().isEmpty()) {
                chatSession.addAssistantMessage(request.sessionId(),
                        response.getResults().get(0).getOutput());
            }

            return ResponseEntity.ok(Map.of(
                    "text", textResponse.toString(),
                    "thinking", thinkingResponse.toString(),
                    "toolCalls", capturedCalls.stream()
                            .map(c -> Map.<String, Object>of(
                                    "name", c.name(),
                                    "input", c.input(),
                                    "output", c.output()))
                            .toList()
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    public record ChatRequest(String message, String agentId, String sessionId) {}
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :claude-learn:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual smoke test**

Run backend: `./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp`

In another terminal:
```bash
# Test agent list
curl http://localhost:8080/api/agents
# Expected: [{"agentId":"s01","name":"S01 Agent Loop","enabled":true}]

# Test chat
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"hi","agentId":"s01","sessionId":"test-123"}'
# Expected: {"text":"...","thinking":"","toolCalls":[]}
```

- [ ] **Step 4: Commit**

```bash
git add claude-learn/src/main/java/com/demo/learn/web/ChatController.java
git commit -m "feat(web): add ChatController with /api/chat and /api/agents endpoints"
```

---

### Task 5: Frontend — Install assistant-ui and create chat page

**Files:**
- Create: `web/src/lib/chat-runtime.ts`
- Create: `web/src/components/chat/agent-selector.tsx`
- Create: `web/src/components/chat/chat-page.tsx`
- Create: `web/src/app/[locale]/(learn)/chat/page.tsx`

- [ ] **Step 1: Install assistant-ui**

Run: `cd web && npm install @assistant-ui/react`

- [ ] **Step 2: Create chat-runtime.ts**

This adapter connects assistant-ui to the Spring Boot backend. It implements assistant-ui's `ChatModelAdapter` to call our `/api/chat` endpoint.

File: `web/src/lib/chat-runtime.ts`

```typescript
const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface ChatRequest {
  message: string;
  agentId: string;
  sessionId: string;
}

export interface ToolCall {
  name: string;
  input: string;
  output: string;
}

export interface ChatResponse {
  text: string;
  thinking: string;
  toolCalls: ToolCall[];
}

export async function sendChatMessage(
  request: ChatRequest
): Promise<ChatResponse> {
  const res = await fetch(`${API_URL}/api/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "Request failed" }));
    throw new Error(err.error || `HTTP ${res.status}`);
  }

  return res.json();
}

export interface AgentInfo {
  agentId: string;
  name: string;
  enabled: boolean;
}

export async function fetchAgents(): Promise<AgentInfo[]> {
  const res = await fetch(`${API_URL}/api/agents`);
  if (!res.ok) throw new Error("Failed to fetch agents");
  return res.json();
}
```

- [ ] **Step 3: Create agent-selector.tsx**

File: `web/src/components/chat/agent-selector.tsx`

```tsx
"use client";

import { AgentInfo } from "@/lib/chat-runtime";

interface AgentSelectorProps {
  agents: AgentInfo[];
  selected: string;
  onChange: (agentId: string) => void;
}

export function AgentSelector({ agents, selected, onChange }: AgentSelectorProps) {
  return (
    <select
      value={selected}
      onChange={(e) => onChange(e.target.value)}
      className="rounded-md border border-zinc-300 bg-white px-3 py-1.5 text-sm
                 dark:border-zinc-700 dark:bg-zinc-800"
    >
      {agents.map((a) => (
        <option key={a.agentId} value={a.agentId} disabled={!a.enabled}>
          {a.name} {!a.enabled && "(coming soon)"}
        </option>
      ))}
    </select>
  );
}
```

- [ ] **Step 4: Create chat-page.tsx**

The main chat UI component. This is a simplified implementation that manages messages state directly and renders them with Tailwind styling — thinking blocks (collapsible, gray), tool calls (expandable), and assistant text.

File: `web/src/components/chat/chat-page.tsx`

```tsx
"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import {
  sendChatMessage,
  fetchAgents,
  type AgentInfo,
  type ToolCall,
} from "@/lib/chat-runtime";
import { AgentSelector } from "./agent-selector";

interface Message {
  role: "user" | "assistant";
  text: string;
  thinking?: string;
  toolCalls?: ToolCall[];
  error?: string;
}

export function ChatPage() {
  const [agents, setAgents] = useState<AgentInfo[]>([]);
  const [selectedAgent, setSelectedAgent] = useState("s01");
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [sessionId] = useState(() => crypto.randomUUID());
  const [expandedTools, setExpandedTools] = useState<Set<number>>(new Set());
  const [expandedThinking, setExpandedThinking] = useState<Set<number>>(new Set());
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchAgents()
      .then(setAgents)
      .catch(() => setAgents([{ agentId: "s01", name: "S01 Agent Loop", enabled: true }]));
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const toggleTool = useCallback((idx: number) => {
    setExpandedTools((prev) => {
      const next = new Set(prev);
      next.has(idx) ? next.delete(idx) : next.add(idx);
      return next;
    });
  }, []);

  const toggleThinking = useCallback((idx: number) => {
    setExpandedThinking((prev) => {
      const next = new Set(prev);
      next.has(idx) ? next.delete(idx) : next.add(idx);
      return next;
    });
  }, []);

  const handleSend = useCallback(async () => {
    const trimmed = input.trim();
    if (!trimmed || loading) return;

    const userMsg: Message = { role: "user", text: trimmed };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setLoading(true);

    try {
      const res = await sendChatMessage({
        message: trimmed,
        agentId: selectedAgent,
        sessionId,
      });
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          text: res.text,
          thinking: res.thinking || undefined,
          toolCalls: res.toolCalls.length > 0 ? res.toolCalls : undefined,
        },
      ]);
    } catch (e) {
      setMessages((prev) => [
        ...prev,
        { role: "assistant", text: "", error: (e as Error).message },
      ]);
    } finally {
      setLoading(false);
    }
  }, [input, loading, selectedAgent, sessionId]);

  return (
    <div className="flex h-[calc(100vh-8rem)] flex-col">
      {/* Header */}
      <div className="flex items-center gap-4 border-b border-zinc-200 pb-3 dark:border-zinc-700">
        <h1 className="text-lg font-semibold">Chat</h1>
        <AgentSelector
          agents={agents}
          selected={selectedAgent}
          onChange={setSelectedAgent}
        />
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto py-4 space-y-4">
        {messages.map((msg, i) => (
          <div
            key={i}
            className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
          >
            <div
              className={`max-w-[80%] rounded-lg px-4 py-2.5 text-sm ${
                msg.role === "user"
                  ? "bg-blue-600 text-white"
                  : "bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100"
              }`}
            >
              {/* Error */}
              {msg.error && (
                <p className="text-red-500">Error: {msg.error}</p>
              )}

              {/* Thinking block */}
              {msg.thinking && (
                <div className="mb-2">
                  <button
                    onClick={() => toggleThinking(i)}
                    className="flex items-center gap-1 text-xs text-zinc-500 hover:text-zinc-700 dark:text-zinc-400"
                  >
                    <span>{expandedThinking.has(i) ? "▼" : "▶"}</span>
                    Thinking...
                  </button>
                  {expandedThinking.has(i) && (
                    <pre className="mt-1 whitespace-pre-wrap rounded bg-zinc-200 p-2 text-xs text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300">
                      {msg.thinking}
                    </pre>
                  )}
                </div>
              )}

              {/* Tool calls */}
              {msg.toolCalls?.map((tc, j) => {
                const toolIdx = i * 100 + j;
                return (
                  <div key={j} className="mb-2">
                    <button
                      onClick={() => toggleTool(toolIdx)}
                      className="flex items-center gap-1 text-xs text-amber-600 hover:text-amber-700 dark:text-amber-400"
                    >
                      <span>{expandedTools.has(toolIdx) ? "▼" : "▶"}</span>
                      🔧 {tc.name}: {tc.input.substring(0, 60)}
                      {tc.input.length > 60 ? "..." : ""}
                    </button>
                    {expandedTools.has(toolIdx) && (
                      <pre className="mt-1 whitespace-pre-wrap rounded bg-zinc-200 p-2 text-xs text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300">
                        {tc.output}
                      </pre>
                    )}
                  </div>
                );
              })}

              {/* Text */}
              {msg.text && <p className="whitespace-pre-wrap">{msg.text}</p>}
            </div>
          </div>
        ))}
        {loading && (
          <div className="text-sm text-zinc-400">Thinking...</div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="flex gap-2 border-t border-zinc-200 pt-3 dark:border-zinc-700">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          placeholder="Type a message..."
          rows={1}
          className="flex-1 resize-none rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm
                     dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
        />
        <button
          onClick={handleSend}
          disabled={loading || !input.trim()}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm text-white
                     disabled:opacity-50 hover:bg-blue-700"
        >
          Send
        </button>
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Create page.tsx**

File: `web/src/app/[locale]/(learn)/chat/page.tsx`

```tsx
"use client";

import { ChatPage } from "@/components/chat/chat-page";

export default function Page() {
  return <ChatPage />;
}
```

- [ ] **Step 6: Verify frontend builds**

Run: `cd web && npm run build`
Expected: Build succeeds, chat page exported as static HTML.

- [ ] **Step 7: Commit**

```bash
git add web/src/lib/chat-runtime.ts web/src/components/chat/ web/src/app/[locale]/(learn)/chat/page.tsx web/package.json web/package-lock.json
git commit -m "feat(web): add chat page with agent selector and tool call display"
```

---

### Task 6: End-to-end integration test

- [ ] **Step 1: Start backend**

Terminal 1:
```bash
./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp
```
Expected: Spring Boot starts on :8080

- [ ] **Step 2: Start frontend**

Terminal 2:
```bash
cd web && npm run dev
```
Expected: Next.js dev server on :3000

- [ ] **Step 3: Test in browser**

Open: `http://localhost:3000/learn-claude-code/zh/chat`

1. Type "hi" in the chat input, press Enter
2. Expected: AI response appears in a chat bubble
3. Type "list the files in current directory"
4. Expected: Tool call block appears (bash: ls), expandable to show output
5. Response text appears after tool execution

- [ ] **Step 4: Commit any fixes if needed**

```bash
git add -u
git commit -m "fix(web): integration test fixes"
```

---

### Task 7: Add sidebar link to chat page

**Files:**
- Modify: `web/src/components/layout/sidebar.tsx`

- [ ] **Step 1: Add chat link to sidebar**

Add a "Chat" link at the top of the sidebar, before the layer sections, linking to the chat page.

In `sidebar.tsx`, add after the `<nav>` opening tag and before the `{LAYERS.map(...)}` block:

```tsx
<div className="pb-4 border-b border-zinc-200 dark:border-zinc-700">
  <Link
    href={`/${locale}/chat`}
    className={cn(
      "flex items-center gap-2 rounded-md px-2.5 py-1.5 text-sm transition-colors",
      pathname.includes("/chat")
        ? "bg-zinc-100 font-medium text-zinc-900 dark:bg-zinc-800 dark:text-white"
        : "text-zinc-500 hover:bg-zinc-50 hover:text-zinc-700 dark:text-zinc-400 dark:hover:bg-zinc-800/50 dark:hover:text-zinc-300"
    )}
  >
    <span className="text-base">💬</span>
    <span>Chat</span>
  </Link>
</div>
```

- [ ] **Step 2: Verify sidebar shows chat link**

Run: `cd web && npm run dev`
Expected: Sidebar on all pages shows a "Chat" link at the top.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/layout/sidebar.tsx
git commit -m "feat(web): add chat link to sidebar"
```
