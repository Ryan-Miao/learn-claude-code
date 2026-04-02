# Learn Claude Code (Java Edition)

> Fork from [shareAI-lab/learn-claude-code](https://github.com/shareAI-lab/learn-claude-code) via [abel533/learn-claude-code](https://github.com/abel533/learn-claude-code)
>
> 原项目为 Python 版，本项目用 **Java 25 + Spring Boot 4.0 + Spring AI 2.0** 重写，并补充了源码追踪和设计模式分析。

[English](./README-en.md) | [中文](./README.md) | [日本語](./README-ja.md)

## 这是什么

一个 12 课递进式的 **Agent Harness 工程学习项目**——从零构建围绕 AI 模型的工作环境（harness）。

原项目用 Python 逆向工程 Claude Code 的架构，本项目用 Java + Spring AI 重写每一课，并在文档中深入追踪 Spring AI 框架源码，理解其设计模式。

### 核心理念

**Agent 是模型，不是框架。** Harness 是你写的代码——工具、知识、上下文管理、权限边界。模型做决策，Harness 执行。

```
Harness = Tools + Knowledge + Observation + Action + Permissions

Claude Code = Agent Loop + @Tool + Context Compression + SubAgent + TaskSystem + Permissions
```

### 相比原项目的 Java 特色

| 特性 | 说明 |
|---|---|
| **多模型兼容** | 通过 OpenAI 协议适配 DeepSeek、智谱 GLM、通义千问等国产模型 |
| **`@Tool` 注解** | Spring AI 自动完成工具调用循环，无需手写 while |
| **Java 25 虚拟线程** | 轻量级并发，实现后台任务与多智能体协作 |
| **源码追踪** | 文档深入到 Spring AI 框架源码，理解递归循环、Advisor Chain 等设计模式 |
| **每课独立可运行** | `./gradlew run -PmainClass=...` 一行命令启动 |

## 12 课递进路线

```
第一阶段: 循环                       第二阶段: 规划与知识
==================                   ==============================
s01  Agent 循环              [1]     s03  TodoWrite               [5]
     ChatClient + @Tool                   TodoManager + nag 提醒
     |                                    |
     +-> s02  Tool Use            [4]     s04  子智能体             [5]
              @Tool 注册多个工具              每个子智能体独立 ChatClient
                                              |
                                         s05  Skills               [5]
                                              SKILL.md 通过 tool_result 注入
                                              |
                                         s06  Context Compact      [5]
                                              三层上下文压缩

第三阶段: 持久化                     第四阶段: 团队
==================                   =====================
s07  任务系统                [8]     s09  智能体团队             [9]
     文件持久化 CRUD + 依赖图             队友 + JSONL 邮箱
     |                                    |
s08  后台任务                [6]     s10  团队协议               [12]
     虚拟线程 + 通知队列                  关机 + 计划审批 FSM
                                          |
                                     s11  自治智能体             [14]
                                          空闲轮询 + 自动认领
                                     |
                                     s12  Worktree 隔离          [16]
                                          任务协调 + 按需隔离执行通道

                                     [N] = 工具数量
```

| 课程 | 主题 | 格言 |
|------|------|------|
| [s01](./docs/zh/s01-the-agent-loop.md) | Agent 循环 | *One loop & Bash is all you need* |
| [s02](./docs/zh/s02-tool-use.md) | Tool Use | *加一个工具, 只加一个 handler* |
| [s03](./docs/zh/s03-todo-write.md) | TodoWrite | *没有计划的 agent 走哪算哪* |
| [s04](./docs/zh/s04-subagent.md) | 子智能体 | *大任务拆小, 每个小任务干净的上下文* |
| [s05](./docs/zh/s05-skill-loading.md) | Skills | *用到什么知识, 临时加载什么知识* |
| [s06](./docs/zh/s06-context-compact.md) | Context Compact | *上下文总会满, 要有办法腾地方* |
| [s07](./docs/zh/s07-task-system.md) | 任务系统 | *大目标要拆成小任务, 排好序, 记在磁盘上* |
| [s08](./docs/zh/s08-background-tasks.md) | 后台任务 | *慢操作丢后台, agent 继续想下一步* |
| [s09](./docs/zh/s09-agent-teams.md) | 智能体团队 | *任务太大一个人干不完, 要能分给队友* |
| [s10](./docs/zh/s10-team-protocols.md) | 团队协议 | *队友之间要有统一的沟通规矩* |
| [s11](./docs/zh/s11-autonomous-agents.md) | 自治智能体 | *队友自己看看板, 有活就认领* |
| [s12](./docs/zh/s12-worktree-task-isolation.md) | Worktree + 任务隔离 | *各干各的目录, 互不干扰* |

## 快速开始

### 环境要求

- **JDK 25+**
- **Gradle 9.4+**（项目自带 wrapper，无需安装）
- 一个兼容 OpenAI 协议的大模型 API Key

### 克隆与构建

```sh
git clone https://github.com/Ryan-Miao/learn-claude-code
cd learn-claude-code
./gradlew build
```

### 设置环境变量

```sh
# Linux / macOS
export AI_API_KEY=your-api-key
export AI_BASE_URL=https://api.deepseek.com    # 替换为你的模型服务商地址
export AI_MODEL=deepseek-chat                   # 替换为你使用的模型名称

# Windows PowerShell
$env:AI_API_KEY="your-api-key"
$env:AI_BASE_URL="https://api.deepseek.com"
$env:AI_MODEL="deepseek-chat"
```

### 运行课程

```sh
# 从第一课开始
./gradlew run -PmainClass=io.mybatis.learn.s01.S01AgentLoop

# 完整递进终点
./gradlew run -PmainClass=io.mybatis.learn.s12.S12WorktreeIsolation

# 总纲: 全部机制合一
./gradlew run -PmainClass=io.mybatis.learn.full.SFullAgent
```

### Web 学习平台

交互式可视化、分步动画、源码查看器：

```sh
cd web && npm install && npm run dev   # http://localhost:3000
```

在线访问：https://ryan-miao.github.io/learn-claude-code/

## 项目结构

```
learn-claude-code/
|
|-- src/main/java/io/mybatis/learn/   # Java 实现 (Spring AI + Spring Boot)
|   |-- core/                         #   公共工具类 (AgentRunner, BashTool 等)
|   |-- core/config/                  #   AiConfig 多 Provider 切换
|   |-- core/tools/                   #   BashTool, ReadFileTool, WriteFileTool, EditFileTool
|   |-- s01/ ~ s12/                   #   12 课递进
|   +-- full/                         #   总纲: 全部机制合一
|
|-- docs/{en,zh,ja}/                  # 文档 (3 种语言, 含源码追踪)
|-- web/                              # 交互式学习平台 (Next.js)
|-- skills/                           # s05 的 Skill 文件
+-- build.gradle.kts                  # Spring Boot 4.0.4 + Spring AI 2.0.0-M4
```

## 文档亮点

除了原项目的心智模型和架构解析，Java 版文档新增：

- **Spring AI 源码追踪**：从 `chatClient.call()` 到 `AnthropicChatModel.internalCall()` 的完整调用链
- **递归循环机制**：`internalCall()` 如何通过递归（而非 while）实现工具调用循环
- **循环退出条件**：`isToolExecutionRequired()` vs `returnDirect()` 的两种退出路径
- **6 层设计模式**：Builder、Fluent API、Advisor Chain、Strategy、Observation、Recursive + Callback
- **工具设计模式**：万能/文件级/片段级/只读的四层粒度，`@Tool` description 对 AI 准确率的影响

## 致谢

- 原项目 [shareAI-lab/learn-claude-code](https://github.com/shareAI-lab/learn-claude-code) — Agent Harness 工程的 Python 教学版本
- [abel533/learn-claude-code](https://github.com/abel533/learn-claude-code) — 上游 Fork

## 许可证

MIT

---

**模型就是 Agent。代码是 Harness。造好 Harness，Agent 会完成剩下的。**
