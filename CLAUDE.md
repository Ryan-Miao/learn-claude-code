# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 构建与运行命令

### 后端 (Java/Spring Boot)

```bash
./gradlew build                                          # 构建全部
./gradlew :claude-learn:run -PmainClass=com.demo.learn.s01.S01AgentLoop  # 运行课程
./gradlew :claude-learn:run -PmainmainClass=com.demo.learn.web.WebChatApp # 运行 Web 聊天后端 (端口 8080)
./gradlew :claude-learn:run -PmainClass=com.demo.learn.s01.S01AgentLoop -PanthropicLog=debug  # 调试 HTTP 日志
```

API 配置通过环境变量：`AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL`。本地覆盖配置在 `claude-learn/src/main/resources/application-local.yml`（已 gitignore）。

### 前端 (Next.js)

```bash
cd web && npm install       # 安装依赖（extract 脚本作为 preinstall 钩子自动运行）
./dev.sh                    # 重启开发服务器（自动 kill 端口 3000 占用，然后启动）
npm run dev                 # 或者直接启动（端口 3000）
npm run build           # 静态导出到 web/out/
npx tsc --noEmit        # 仅类型检查
```

前端通过 `NEXT_PUBLIC_API_URL` 环境变量连接后端（默认 `http://192.168.3.7:8080`）。

### 前端调试规则

**端口占用处理：**
- 当端口 3000 被占用时，只 kill 该端口的进程：`lsof -ti:3000 | xargs kill -9`
- **禁止** `killall node` 或 `pkill node`，这会杀掉所有 node 进程（后端、MCP server 等）
- kill 后重新在 3000 端口启动，**不要自动换端口**

**访问地址：**
- 项目配置了 `basePath: "/learn-claude-code"`（见 `web/next.config.ts`）
- 路由使用 `[locale]` 动态段 (zh/en/ja)
- 正确访问地址：`http://localhost:3000/learn-claude-code/zh`
- 直接访问 `http://localhost:3000` 或 `http://localhost:3000/learn-claude-code` 会 404

## 架构

**Java 25 + Spring Boot 4.0 + Spring AI 2.0** 后端，**Next.js 16 + React 19 + Tailwind CSS v4** 前端。以静态站点部署到 GitHub Pages。

### 后端 (`claude-learn/src/main/java/com/demo/learn/`)

12 节课程 (s01-s12) 各自是独立的 `@SpringBootApplication`，包含 `CommandLineRunner`。通过 `scanBasePackages = "com.demo.learn.core"` 共享基础设施。

- `core/config/` — `AiConfig`：多供应商 ChatModel (openai/anthropic)，通过 `ai.provider` 属性选择
- `core/tools/` — `BashTool`、`ReadFileTool`、`WriteFileTool`、`EditFileTool`、`PathValidator`、`ToolCallLoggingAdvisor`
- `core/` — `AgentRunner`：带重试逻辑的交互式 REPL 循环
- `core/team/` — `MessageBus`：Agent 间通信
- `web/` — Web 聊天：`ChatController`（REST API）、`AgentRegistry`、`HttpCaptureAdvisor`（拦截 LLM 调用供前端监控面板使用）
- `full/` — `SFullAgent`：全部 12 个机制组合

### 前端 (`web/src/`)

使用 App Router，带 `[locale]` 动态路由段 (zh/en/ja)。静态导出 — 无服务端运行时。

- `app/[locale]/(learn)/` — 课程页面、聊天、时间线、差异查看器、架构视图
- `components/visualizations/` — 12 个课程专属的交互式 SVG 可视化组件
- `components/chat/` — 实时 Agent 聊天 UI（调用后端 REST API）
- `components/simulator/` — Agent 循环逐步模拟器
- `data/generated/` — 由 `scripts/extract-content.ts` 从 `docs/` 自动生成（preinstall/prebuild 时运行）
- `i18n/messages/` — zh.json、en.json、ja.json
- `hooks/useDarkMode.ts` — 同时包含 `useSvgPalette()` 用于明暗主题 SVG 颜色

### 关键模式

- 前端**完全静态** — 动态功能（聊天）通过客户端 fetch 调用独立的 Spring Boot 后端
- 无组件库 — 手写 Card、Badge、Tabs
- 无状态管理库 — useState/useRef 配合自定义 hooks
- `globals.css` 中的 CSS 自定义属性定义了 5 层颜色系统 (blue/emerald/purple/amber/red)，用于所有课程
- 国际化：客户端 `I18nProvider` + `useTranslations`，服务端 `getTranslations(locale, namespace)`，回退链：请求语言 → zh → en → 原始 key

## 设计上下文

品牌：**清晰、渐进、技术感**

目标用户：Java 开发者学习 AI Agent 工程、自学者、采用 AI 工具的团队

美学风格：开发者工具风格（VS Code / Claude Code CLI），dark mode 为默认，code-first

设计原则：

1. **代码即内容** — 代码块是一等公民，深色背景，语法高亮
2. **可视化服务教学** — 动画服务理解，不是装饰
3. **尊重开发者习惯** — dark mode、monospace、keyboard-friendly
4. **渐进式复杂度** — 12 节课渐进式，timeline 可见但不压迫
5. **默认多语言** — zh/en/ja 三语言，所有设计要考虑 CJK 排版

完整设计上下文：[.impeccable.md](./.impeccable.md)

## 安全规则

**提交前必须检查敏感信息**：在执行 `git commit` 之前，必须检查暂存文件中是否包含以下敏感内容：

- API Key / Secret / Token（如 `sk-`、`key-`、`Bearer` 开头的字符串）
- 数据库连接字符串、密码
- `.env`、`credentials`、`secret` 等文件
- `application-local.yml` 等本地配置文件

如发现敏感信息，必须从暂存区移除后再提交，并提醒用户。
