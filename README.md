# Claude Code Java ☕

> 使用 Java + Spring AI 重写的 [Claude Code](https://github.com/anthropics/claude-code) CLI AI 编码助手。

## ✨ 功能特性

- 🤖 **AI 对话** — 流式输出，逐 token 实时显示
- 🔧 **11 个内置工具** — Bash、文件读写编辑、Glob、Grep、Web获取、Agent 等
- ⚡ **双 API 提供者** — 同时支持 OpenAI 兼容 API 和 Anthropic 原生 API
- 📝 **斜杠命令** — `/help`, `/compact`, `/cost`, `/model`, `/history` 等 11 个命令
- 🖥️ **JLine 终端** — 行编辑、历史记录、Tab 补全、多行输入
- 📋 **CLAUDE.md** — 自动加载项目级和用户级记忆文件
- 🎯 **Skills** — 可扩展的技能系统
- 🌿 **Git 上下文** — 自动收集分支、状态、最近提交
- 💾 **对话持久化** — 自动保存对话历史，支持回顾
- 🗜️ **上下文压缩** — AI 生成摘要替代简单清空

## 📦 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 25 | 运行时 |
| Spring Boot | 4.1.0-M2 | 应用框架 |
| Spring AI | 2.0.0-M4 | AI 模型调用 |
| JLine 3 | 3.28.0 | 终端交互 |
| Picocli | 4.7.6 | CLI 命令解析 |

## 🚀 快速开始

### 前置要求

- **JDK 25**（配置 `JAVA_HOME`）
- **Maven 3.9+**
- **API Key**（OpenAI 或 Anthropic 或兼容服务）

### 配置

设置环境变量：

```bash
# 必须：API 密钥
export AI_API_KEY="your-api-key-here"

# 可选：API 提供者（默认 openai）
export CLAUDE_CODE_PROVIDER="openai"    # 或 "anthropic"

# 可选：自定义 API 地址和模型
export AI_BASE_URL="https://api.openai.com"
export AI_MODEL="gpt-4o"
```

**支持的 API 提供者：**

| 提供者 | 默认 Base URL | 默认模型 |
|--------|--------------|---------|
| `openai` | `https://api.openai.com` | `gpt-4o` |
| `anthropic` | `https://api.anthropic.com` | `claude-sonnet-4-20250514` |

> 💡 OpenAI 提供者兼容所有 OpenAI 格式的 API 代理（如 DeepSeek、Azure OpenAI 等）。

### 运行

**Windows PowerShell：**
```powershell
.\run.ps1
```

**Windows CMD：**
```cmd
run.bat
```

**手动运行：**
```bash
cd claude-code-copy
mvn spring-boot:run
```

### 启动效果

```
  ◆ Claude Code (Java)  v0.1.0-SNAPSHOT
  Type /help for commands  •  Ctrl+D to exit

  Provider: OPENAI  Model: deepseek-chat
  API URL:  https://api.deepseek.com
  Work Dir: D:\my-project
  Tools: 11 registered
  Terminal: windows-vtp (160×30)
  Tip: Tab to complete commands, ↑↓ to browse history, Ctrl+D to exit

❯ 
```

## 📖 使用说明

### 对话

直接输入文本与 AI 对话，支持流式输出：

```
❯ 帮我分析这个项目的架构
```

### 多行输入

行末加 `\` 续行：

```
❯ 请帮我写一个函数，\
  ... 实现字符串反转
```

### 斜杠命令

| 命令 | 说明 |
|------|------|
| `/help` | 显示所有可用命令 |
| `/clear` | 清屏 |
| `/compact` | AI 摘要压缩对话上下文 |
| `/cost` | 显示 Token 使用量和费用 |
| `/model [name]` | 查看/切换模型 |
| `/status` | 显示会话状态（消息数、Token、模型） |
| `/context` | 显示已加载的上下文（CLAUDE.md、Skills、Git） |
| `/config` | 查看配置信息 |
| `/init` | 初始化 CLAUDE.md 配置文件 |
| `/history` | 列出保存的对话历史 |
| `/exit` | 退出 |

### 内置工具

AI 可以自动调用以下工具：

| 工具 | 说明 |
|------|------|
| `bash` | 执行 Shell 命令 |
| `file_read` | 读取文件内容 |
| `file_write` | 写入文件 |
| `file_edit` | 编辑文件（搜索替换） |
| `glob` | 文件模式匹配搜索 |
| `grep` | 文本内容搜索 |
| `list_files` | 列出目录文件 |
| `web_fetch` | 获取网页内容 |
| `todo_write` | 管理待办事项 |
| `agent` | 启动子 Agent 处理复杂任务 |
| `notebook_edit` | 编辑 Jupyter Notebook |

### CLAUDE.md 记忆文件

创建 `CLAUDE.md` 文件来给 AI 提供项目上下文：

```markdown
# 项目说明
这是一个 Spring Boot 项目，使用 MyBatis 做数据持久化。

# 编码规范
- 使用中文注释
- 遵循阿里巴巴 Java 开发手册
```

加载顺序（优先级递增）：
1. `~/.claude/CLAUDE.md` — 全局配置
2. 项目根目录 `CLAUDE.md` — 项目级配置
3. 当前目录 `CLAUDE.md` — 目录级配置

### Skills 技能系统

在以下目录放置 `.md` 文件作为技能：

- `~/.claude/skills/` — 全局技能
- `.claude/skills/` — 项目技能
- `.claude/commands/` — 自定义命令技能

技能文件支持 YAML frontmatter：

```markdown
---
name: code-review
description: 代码审查技能
---

请按照以下标准审查代码：
1. 命名规范
2. 错误处理
3. 性能考量
```

## 🏗️ 架构设计

### 模块结构

```
com.claudecode
├── ClaudeCodeApplication          // Spring Boot 主入口
├── cli/
│   └── ClaudeCodeRunner           // 启动编排（CommandLineRunner）
├── config/
│   └── AppConfig                  // Bean 装配、Provider 切换
├── core/
│   ├── AgentLoop                  // Agent 循环（阻塞 + 流式）
│   ├── TokenTracker               // Token 使用追踪
│   └── ConversationPersistence    // 对话持久化
├── tool/
│   ├── Tool                       // 工具协议接口
│   ├── ToolRegistry               // 工具注册中心
│   ├── ToolCallbackAdapter        // Spring AI 适配器
│   └── impl/                      // 11 个工具实现
├── command/
│   ├── SlashCommand               // 命令接口
│   ├── CommandRegistry            // 命令注册中心
│   └── impl/                      // 11 个命令实现
├── console/
│   ├── BannerPrinter              // 启动 Banner
│   ├── ToolStatusRenderer         // 工具状态渲染
│   ├── ThinkingRenderer           // Thinking 渲染
│   ├── SpinnerAnimation           // 加载动画
│   ├── MarkdownRenderer           // Markdown 渲染
│   └── AnsiStyle                  // ANSI 样式工具
├── context/
│   ├── SystemPromptBuilder        // 系统提示词构建
│   ├── ClaudeMdLoader             // CLAUDE.md 加载
│   ├── SkillLoader                // Skills 加载
│   └── GitContext                 // Git 上下文收集
└── repl/
    ├── ReplSession                // REPL 会话管理
    └── ClaudeCodeCompleter        // Tab 补全
```

### 核心流程

```
用户输入 → 命令检测 → AgentLoop
                        ↓
              chatModel.stream(prompt) → Flux<ChatResponse>
                        ↓
              逐token实时输出到终端
                        ↓
              检测工具调用 → 执行工具 → 结果回传
                        ↓
              继续循环或结束
```

### 双 API 提供者架构

```
                    ┌─ openAiChatModel ──── OpenAI / DeepSeek / Azure
CLAUDE_CODE_PROVIDER─┤
                    └─ anthropicChatModel ── Anthropic Claude
```

通过 `claude-code.provider` 配置决定使用哪个 `ChatModel` Bean。

## ⚙️ 配置参考

### application.yml

```yaml
spring:
  ai:
    anthropic:
      api-key: ${AI_API_KEY:}
      base-url: ${AI_BASE_URL:https://api.anthropic.com}
      chat.options.model: ${AI_MODEL:claude-sonnet-4-20250514}
    openai:
      api-key: ${AI_API_KEY:}
      base-url: ${AI_BASE_URL:https://api.openai.com}
      chat.options.model: ${AI_MODEL:gpt-4o}

claude-code:
  provider: ${CLAUDE_CODE_PROVIDER:openai}
```

### 环境变量

| 变量 | 必须 | 说明 | 默认值 |
|------|------|------|--------|
| `AI_API_KEY` | ✅ | API 密钥 | - |
| `CLAUDE_CODE_PROVIDER` | ❌ | 提供者 (`openai`/`anthropic`) | `openai` |
| `AI_BASE_URL` | ❌ | API 基础 URL | 按提供者不同 |
| `AI_MODEL` | ❌ | 模型名称 | 按提供者不同 |
| `AI_MAX_TOKENS` | ❌ | 最大 Token 数 | `8096` |

## 🔧 开发

### 构建

```bash
mvn clean compile
```

### 打包

```bash
mvn clean package -DskipTests
java -jar target/claude-code-java-0.1.0-SNAPSHOT.jar
```

### 已知问题

- **Windows 编码**：需要 `chcp 65001` 切换到 UTF-8 编码页（启动脚本已自动处理）
- **IDE 终端**：IntelliJ IDEA 等 IDE 内置终端为 dumb 模式，Tab 补全和行编辑受限
- **JDK 25 警告**：Maven 的 jansi/guava 会触发 native access 警告（启动脚本已通过 MAVEN_OPTS 抑制）

## 📝 对应关系

| Claude Code (TypeScript) | Java 实现 | 说明 |
|--------------------------|-----------|------|
| `cli.tsx` → `main.tsx` | `ClaudeCodeApplication` + `ClaudeCodeRunner` | 入口 |
| `REPL.tsx` | `ReplSession` + JLine 3 | 交互循环 |
| `query.ts` | `AgentLoop` | Agent 循环 |
| `Tool.ts` + `tools/*` | `Tool` 接口 + `impl/*` | 工具系统 |
| `commands.ts` | `SlashCommand` + `impl/*` | 命令系统 |
| `context.ts` + `prompts.ts` | `SystemPromptBuilder` + loaders | 上下文 |
| `CLAUDE.md` + `skills/` | `ClaudeMdLoader` + `SkillLoader` | 记忆/技能 |
| Ink Components | `console/*` 渲染器 | 终端 UI |

## 📄 License

本项目仅用于学习和研究目的。
