# Chat Web UI Design Spec

## Goal

Add an interactive chat web interface to the learn-claude-code project so users can learn AI agent patterns through a browser instead of the command line. The UI displays thinking blocks, tool calls, and AI responses like Claude Code does.

## Architecture

**Backend**: Spring Boot REST API (port 8080)
**Frontend**: Next.js page using [assistant-ui](https://github.com/assistant-ui/assistant-ui) React components

The existing `output: "export"` static export in Next.js is preserved. The chat page is a client-side rendered page that fetches from the Spring Boot backend at runtime.

## Backend — Spring Boot REST API

### New package: `com.demo.learn.web`

**`WebChatApp.java`** — Spring Boot entry point with `@SpringBootApplication(scanBasePackages = {"com.demo.learn.core", "com.demo.learn.web"})`. Starts Tomcat (no `WebApplicationType.NONE`). Port 8080. Existing S01-S12 CLI entry points are unaffected — they are separate main classes that still set `WebApplicationType.NONE`.

**`ChatController.java`** — REST controller with two endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/agents` | Returns list of available agents |
| POST | `/api/chat` | Send message, get AI response |

`GET /api/agents` response:
```json
[{ "agentId": "s01", "name": "S01 Agent Loop", "enabled": true }]
```

`POST /api/chat` request:
```json
{ "message": "hello", "agentId": "s01", "sessionId": "uuid-1234" }
```

`POST /api/chat` response:
```json
{
  "text": "AI reply text",
  "thinking": "Thinking process (may be empty)",
  "toolCalls": [
    { "name": "bash", "input": "ls -la", "output": "file listing..." }
  ]
}
```

Tool call output is truncated to 2000 characters with "... (truncated)" indicator to keep response size reasonable.

### Conversation context

The backend maintains an in-memory conversation history per session using `ConcurrentHashMap<String, List<Message>>`, keyed by `sessionId`. The frontend generates a UUID on first load and sends it with each request. Refreshing the page generates a new session ID (clearing history). No persistence.

### Agent and tool interception

**`AgentRegistry.java`** — Registers agent definitions (system prompt + tools). Initially only S01. Adding a new agent is one line of registration.

**`CaptureToolCallback.java`** — A wrapper that decorates each tool's `ToolCallback`. When a tool is executed, it captures the input and output into a shared list. This is the correct interception point because `ToolCallback.call()` is invoked for each tool execution inside ChatClient's agentic loop, giving us both input and actual output.

**Thinking extraction** — After `ChatClient.call()` completes, thinking blocks are extracted from the final `ChatResponse` metadata. Spring AI's Anthropic integration includes thinking content in the response.

The controller combines captured tool calls + thinking + final text into the JSON response.

**CORS config** — Allow `localhost:3000` for Next.js dev server access.

### Reuse

- `AiConfig` — existing `@Component` that provides the `ChatModel` bean
- Tools from `core/tools/` — `BashTool`, `ReadFileTool`, etc. (wrapped by `CaptureToolCallback`)
- No changes to existing S01-S12 command-line entry points

## Frontend — Next.js + assistant-ui

### Dependencies

```
npm install @assistant-ui/react
```

### New page: `web/src/app/[locale]/(learn)/chat/page.tsx`

Client-side rendered (`"use client"`). Uses the existing `(learn)` layout with sidebar.

### Component structure

```
ChatPage
├── AgentSelector — dropdown (S01 enabled, others disabled)
└── AssistantRuntimeProvider (assistant-ui)
    └── Thread
        ├── UserMessage — user bubble
        ├── AssistantMessage — AI reply
        │   ├── ThinkingBlock (collapsible, gray background)
        │   └── ToolCallBlock (tool name + input, expandable output)
        └── Composer (input + send button)
```

### Data flow

1. User types message, hits send
2. Custom runtime adapter (`web/src/lib/chat-runtime.ts`) calls `fetch` to `POST /api/chat` with `{message, agentId, sessionId}`
3. Response `{text, thinking, toolCalls}` is mapped to assistant-ui's `ChatModelAdapter` message format:
   - `thinking` → `ThinkingContentPart`
   - `toolCalls[i]` → `ToolCallContentPart` (with name, input, output rendered in expandable UI)
   - `text` → `TextContentPart`
4. Components render the structured message parts

### Environment variable

`NEXT_PUBLIC_API_URL` — defaults to `http://localhost:8080`. Configurable for different environments.

### Styling

Reuses existing Tailwind config. assistant-ui components styled with Tailwind class overrides.

## Development Workflow

1. Terminal 1: `./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp` (backend on :8080)
2. Terminal 2: `cd web && npm run dev` (frontend on :3000)
3. Browser: `http://localhost:3000/learn-claude-code/zh/chat`

Note: `basePath: "/learn-claude-code"` in next.config.ts prefixes all routes.

## Scope — What We Build Now

- S01 agent only (Agent Loop — basic chat with bash tool)
- Non-streaming (wait for full response)
- In-memory session conversation history (refresh clears)
- No authentication

## Scope — What We Don't Build (YAGNI)

- Streaming responses (can add later)
- Conversation history persistence (database/filesystem)
- User authentication
- Modifying existing S01-S12 CLI entry points
- Multi-agent in one conversation
