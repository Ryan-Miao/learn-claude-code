# Release Notes

## v0.1.0 — 首个正式版本 🎉

> **发布日期**: 2025-07

### 概述

Claude Code Java 是使用 Java + Spring AI 重写的 [Claude Code](https://github.com/anthropics/claude-code) CLI AI 编码助手。本次发布是首个正式版本，涵盖了原版约 **90%** 的本地可实现功能。

### 技术栈

| 组件 | 版本 |
|------|------|
| JDK | 25 |
| Spring Boot | 4.1.0-M2 |
| Spring AI | 2.0.0-M4 |
| Jink (TUI) | 0.5.0 |
| JLine 3 | 3.28.0 |

### 核心特性

#### 🤖 AI 对话
- 流式输出，逐 token 实时显示
- 双 API 提供者：OpenAI 兼容 API + Anthropic 原生 API
- Thinking 模式支持（展示 AI 思考过程）
- 三层上下文自动压缩（微压缩 → Session Memory → 全量压缩）

#### 🔧 34 个内置工具
- **文件操作**: FileRead（含图片支持）、FileWrite、FileEdit（带 diff 输出）
- **搜索**: Glob、Grep、ListFiles
- **执行**: Bash（沙箱模式）、WebFetch、WebSearch
- **任务管理**: TaskCreate/Get/List/Update、TaskStop、TaskOutput
- **Agent**: Agent（子 Agent）、AskUser（结构化提问）
- **MCP**: McpToolBridge（远程工具桥接）
- **扩展**: Config（持久化配置）、NotebookEdit、Sleep
- **诊断**: LSPTool、BriefTool、NotificationTool、ToolSearch

#### ⌨️ 54 个斜杠命令
- **基础**: /help（带搜索）、/clear、/compact（统计+激进模式）、/cost、/model、/status
- **文件**: /diff、/files、/commit（push/PR 支持）
- **记忆**: /memory、/skills、/init
- **对话**: /resume、/export、/history、/branch、/rewind、/tag
- **审查**: /review、/security-review
- **MCP/插件**: /mcp、/plugin、/permissions、/tasks
- **UX**: /brief、/vim、/theme、/usage、/tips、/output-style、/keybindings
- **隐私**: /privacy、/feedback
- **调试**: /debug、/trace、/heapdump、/ctx-viz、/sandbox、/reset-limits、/env、/performance

#### 🖥️ 全屏 TUI（Jink 框架）
- 基于 [Jink](https://github.com/abel533/jink) 的 React-like 终端 UI
- 全屏渲染（alternate screen buffer）
- 消息列表、状态栏、输入区分离布局
- 鼠标追踪支持

#### 🔒 多级权限管理
- 5 种权限模式：DEFAULT / ACCEPT_EDITS / BYPASS / DONT_ASK / PLAN
- 规则引擎自动评估（7 步评估链）
- 30+ 危险命令模式检测
- Y/A/N/D 四选项确认 UI
- 拒绝追踪（连续 3 次 / 累计 20 次自动降级）

#### 📋 CLAUDE.md 记忆系统
- 三级加载：全局 → 项目 → 目录
- Session Memory 持久化
- Skills 技能系统（全局 + 项目）

#### 🔌 扩展能力
- **MCP 协议**: StdIO 传输、多服务器管理、工具发现与桥接
- **插件系统**: JAR 插件（ClassLoader 隔离）、Marketplace、自动更新
- **Hook 系统**: PreToolUse / PostToolUse / PrePrompt / PostResponse
- **Plan 模式**: 结构化规划，只读操作允许
- **Coordinator**: 多 Agent 协调模式

#### 🏗️ 基础设施
- WebSocket Server Mode（远程连接）
- Git Worktree 隔离
- LSP 集成（语言服务器协议客户端）
- 基础遥测（功能开关 + 指标收集）
- RateLimiter、TokenEstimation、通知服务
- 内部日志系统（会话日志 + 调试日志 + 导出）

### 代码质量

- **184 源文件**（176 main + 8 test）
- **~26.7K 行** Java 代码
- **87 单元测试**（全部通过）
- 代码结构重构：提取 7 个共享组件，消除 ~560 行重复代码

### 构建与运行

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/claude-code-java-0.1.0.jar

# 或构建发行版（含 jlink 精简 JRE）
.\packaging\build-dist.ps1    # Windows
./packaging/build-dist.sh      # Linux/macOS
```

### 已知限制

- 不支持 Anthropic OAuth 登录（login/logout）
- 不支持 Bridge 远程环境
- 不支持 Voice 语音输入
- Windows 终端需 `chcp 65001` 切换 UTF-8 编码（发行版启动脚本已自动处理）
- IDE 内置终端（dumb 模式）TUI 功能受限

---

*完整文档请参阅 [README.md](README.md) 和 [BUILD.md](BUILD.md)*
