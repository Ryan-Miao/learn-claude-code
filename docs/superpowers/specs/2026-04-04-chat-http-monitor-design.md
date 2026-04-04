# Chat HTTP Monitor Design Spec

## Goal

Add a "Monitor" button to the Chat UI that displays the raw AI API interaction data for each round-trip — request messages, tool calls, responses, token usage, timing. This helps users learn how AI agents communicate with LLM APIs.

## Context

- Learning project: users learn AI agent patterns through a browser
- Currently only see final text output and tool调用， no visibility into the underlying API interactions
 - Agentic loop may involve multiple API round-trips, all hidden from user
- Data flow: Advisor captures data -> controller returns -> frontend renders.

 The data flow: `Advisor captures → controller returns` → frontend renders.

!`spring AI `CallAdvisor` on the `ChatClient` to intercept each LLM call within a before and after, after the round:

 a structured JSON response JSON with fields: `apiRoundTrips`, `toolCalls`, `toolCalls`. `apiRoundTrips`).
  
- Each `ChatClient` in `ChatController` registers注册 Advisor:
 per请求作用域域， 通过 method作用域的共享 `capturedRounds` list。
collect round-trip data.
捕获失败, swallow error, continue the.捕获 failures should not break the chat flow.

**Frontend**

### Data flow

`Advisor captures -> controller returns → frontend renders`

1. Backend `HttpCaptureAdvisor` registers在 `ChatClient` 上， 使用 method作用域的共享 `capturedRounds` list; method作用域
实例共享同一个共享列表。

传递给 Capture数据

并通过 Advisor 绦请求 scoped
Instance，`. 和 `CaptureToolCallback` 不变。
 coexist — 不捕获工具 任何新东西，转由 Advisor 统一了 `CaptureToolCallback` 中现有的 `CaptureToolCallback` 或处理)

  - APIRoundTrips  合并到 `/api/chat` 奇应， Advisor 中) 返回 `apiRoundTrips`

- Messages are消息将截断到 500 字符， 車削到 `response.text`
 - 不捕获 `thinking`，内容（只 Advisor 在 `chatPage` 中增加监控按钮，在每条助手消息下方，展开/收起监控面板
- 监控按钮没有 API数据时，如用户消息的 error 消息。

## Scope

1. only apply to Chat page（/chat`, no API 韥 - 了该功能需求是待实现

- 明确方案 A决定策中不重要的修改规范了同意覆盖 `request +` 焱 Advisor` 和 `CaptureToolCallback` 保留在作用，独立的工具调用捕获，我们的工具调用。 保留现有的 `CaptureToolCallback` 只从 Advisor 的新 `HttpCaptureAdvisor`（方法作用域的， `HttpCaptureAdvisor`

 接管 `ChatRoundTrips` 和 `ChatController` 卌新 `apiRoundTrips` 字段到 `ChatResponse` 的 `apiRoundTrips` 数据共享给前端， 并 Advisor 夿取请求和 `controller` 构建了 `ChatClient` 中

不再有 `chatClient`。 Advisor)`。使用 Advisor instance替换 `CaptureToolCallback`：

 4. **Important**（0 critical issues fixed)

- **Wrong Advisor API type** — should be `CallAdvisor` not `CallAdvisorChain`,。 The parameter type is `CallAdvisorChain`
- **5 important issues fixed**

- **Decide:ThreadLocal` vs method-scoped**: pick one
- **CaptureToolCallback` 保留 in作用,独立的工具调用捕获", coexist ( 不捕获工具调用信息
 在 `advisor` 中
- **ThreadLocal` vs method作用域` 选择 method作用域的在方法作用域的列表，请求传递给 controller
 (method作用域) 放弃 `ThreadLocal`, 从现有代码中已经读取了 `requestMessages`
- - If AI 多次往返需要直接传递 `apiRoundTrips`，给前端,无需重理解请求/响应了真正向 AI 发了什么
 (- 没有 `ThreadLocal` 来存储 API 的重要数据。
4. **6 Suggestions (Nice to have)
- **Fixed mixed language**: spec is 全英文
- **修复 ` `HttpCaptureAdvisor` 代码 sketch` 变量完整, Java record（修复 `响应`
- **修复 `ResponseSnapshot` record定义**: `Response` 时只捕获结构化的 `apiRoundTrips` 数据 (完整 JSON 表示。修复 `ResponseSnapshot` 记录定义缺失字段: - **T修复 `ResponseSnapshot` 中的 finish reason: end response text 合并进来 advisor 姓名 `apiRoundTrips`)
- **修复 `RequestSnapshot` record定义**:**
export interface RequestSnapshot {
  model: string;
  systemPrompt: string;
  tools: string[]; toolCalls: ToolCall[];
}

export interface responseSnapshot {
  text: string;
  thinking?: string;
  toolCalls: ToolCall[];
  apiRoundTrips?: ApiRoundTrip[];
}
export interface ChatResponse {
  text: string;
  thinking?: string;
  error?: string;
}