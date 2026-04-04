# Chat Web UI Design Spec

## Goal

Add an interactive chat web interface to the learn-claude-code project so users can learn AI agent patterns through a browser instead of the command line. The UI displays thinking blocks, tool calls, and AI responses like Claude Code does.

## Architecture

**Backend**: Spring Boot REST API (port 8080)
**Frontend**: Next.js page using [assistant-ui](https://github.com/assistant-ui/assistant-ui) React components

The existing `output: "export"` static export in Next.js is preserved. The chat page is a client-side rendered page that fetches from the Spring Boot backend at runtime.

## Backend — Spring Boot REST API

### New package: `com.demo.learn.web`

**`WebChatApp.java`** — Spring Boot entry point. Starts Tomcat (no `WebApplicationType.NONE`). Port 8080.

**`ChatController.java`** — REST controller with two endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/agents` | Returns list of available agents |
| POST | `/api/chat` | Send message, get AI response |

`POST /api/chat` request:
```json
{ "message": "hello", "agentId": "s01" }
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

**`AgentRegistry.java`** — Registers agent definitions (system prompt + tools). Initially only S01. Adding a new agent is one line of registration.

**`CaptureAdvisor.java`** — Custom Spring AI `CallAdvisor` that intercepts the ChatClient call chain to capture:
- Thinking blocks from the response
- Tool call name, input, and output
- Final text response

Assembles all captured data into the JSON response above.

**CORS config** — Allow `localhost:3000` for Next.js dev server access.

### Reuse

- `AiConfig` — existing `@Component` that provides the `ChatModel` bean
- Tools from `core/tools/` — `BashTool`, `ReadFileTool`, etc.
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
2. `useExternalStoreRuntime` adapter calls `fetch("http://localhost:8080/api/chat", {method: "POST", body: {message, agentId}})`
3. Response `{text, thinking, toolCalls}` is converted to assistant-ui message format
4. Components render thinking (collapsible), tool calls (expandable), and text reply

### Environment variable

`NEXT_PUBLIC_API_URL` — defaults to `http://localhost:8080`. Configurable for different environments.

### Styling

Reuses existing Tailwind config. assistant-ui components styled with Tailwind class overrides.

## Development Workflow

1. Terminal 1: `./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp` (backend on :8080)
2. Terminal 2: `cd web && npm run dev` (frontend on :3000)
3. Browser: `http://localhost:3000/zh/chat`

## Scope — What We Build Now

- S01 agent only (Agent Loop — basic chat with bash tool)
- Non-streaming (wait for full response)
- No conversation history persistence (refresh clears)
- No authentication

## Scope — What We Don't Build (YAGNI)

- Streaming responses (can add later)
- Conversation history persistence
- User authentication
- Modifying existing S01-S12 CLI entry points
- Multi-agent in one conversation
