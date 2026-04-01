接下来优化cli的交互样式，下面第一段是当前实现的效果，参考第二段和第三段的效果进行优化，参考这两段添加边框和内部的logo（先自行根据当前项目实现一个logo），第二段和第三段在用户输入内容前有个不同颜色的 “●”，代表不同类型的输入。

## 第一段
```
PS D:\Learn\copy-claude-code\claude-code-copy> .\run.ps1

  ◆ Claude Code (Java)  v0.1.0-SNAPSHOT
  Type /help for commands  •  Ctrl+D to exit

  Provider: OPENAI  Model: deepseek-chat
  API URL:  https://api.deepseek.com
  Work Dir: D:\Learn\copy-claude-code\claude-code-copy
  Tools: 11 registered
  Terminal: windows-vtp (125×30)
  Tip: Tab to complete commands, ↑↓ to browse history, Ctrl+D to exit

❯ 今天天气怎么样？

我来帮您查看今天的天气情况。首先让我获取一些上下文信息，然后为您查询天气。  ─────────────────────────────────────────
  ⚙ TodoWrite  running...
  ✓ TodoWrite  done
    ✅ Task added:
      🔴 [ec14f12e] 查询天气信息 - 获取用户位置信息并查询天气 (2026-04-02 13:41)
  ─────────────────────────────────────────
让我先查看当前项目的结构和环境，了解是否有相关的天气API配置：  ─────────────────────────────────────────
  ⚙ ListFiles  running...
  ✓ ListFiles  done
    Directory: D:\Learn\copy-claude-code\claude-code-copy
    Entries: 7

    📄 pom.xml  (4.5 KB)
    📄 run.bat  (1.1 KB)
    📄 run.ps1  (1.5 KB)
    📁 src/
    📁 src/main/
    📄 修改记录.md  (597 B)
    📄 需求文档.md  (33.0 KB)
  ─────────────────────────────────────────
  ─────────────────────────────────────────
  ⚙ Read  running...
    需求文档.md
  ✓ Read  done
       1 | # Claude Code Java 重写
       2 |
       3 | ## 1. 需求背景
       4 |
       5 | Claude Code 是 Anthropic 开发的一款基于 Claude AI 的 CLI 编码助手工具。原版使用 TypeScript + Ink（React终端框架）实现，源码规模约 **2,289 个文件**，分布在 **38 个模块**中，提供了丰富的终端交互体验，包括工具调用、斜杠命令、CLAUDE.md 记忆加载、Skills 技能系统等功能。
       6 |
       7 | 本项目的目标是使用 **Java + Spring AI** 对 Claude Code 的核心功能进行重写，实现一个功能完整、交互体验良好的 CLI AI 编码助手。参考 `claude-code-learn` 项目中的 Spring AI 实践模式（AgentLoop 显式循环、AgentTool 工具协议等），但在 CLI 交互体验上需要做到更加完善。
       8 |
       9 | ### 业务目标
      10 | - 深入理解 Cl...
  ─────────────────────────────────────────
我看到这是一个Java项目，主要用于重写Claude Code的CLI工具。让我查看一下项目结构，看看是否有天气相关的工具或功能：  ─────────────────────────────────────────
  ⚙ Glob  running...
    pattern: **/*.java
  ✓ Glob  done
    Found 48 file(s):
      src/main/java/com/claudecode/ClaudeCodeApplication.java
      src/main/java/com/claudecode/cli/ClaudeCodeRunner.java
      src/main/java/com/claudecode/command/CommandContext.java
      src/main/java/com/claudecode/command/CommandRegistry.java
      src/main/java/com/claudecode/command/SlashCommand.java
      src/main/java/com/claudecode/command/impl/ClearCommand.java
      src/main/java/com/claudecode/command/impl/CompactCommand.java
      src/main/java/com/claudecode/command/impl/ConfigCommand.java...
  ─────────────────────────────────────────
我看到项目中有WebFetchTool工具，可以用来获取天气信息。让我查看一下这个工具的实现：  ─────────────────────────────────────────
  ⚙ Read  running...
    src/main/java/com/claudecode/tool/impl/WebFetchTool.java
  ✓ Read  done
       1 | package com.claudecode.tool.impl;
       2 |
       3 | import com.claudecode.tool.Tool;
       4 | import com.claudecode.tool.ToolContext;
       5 |
       6 | import java.net.URI;
       7 | import java.net.http.HttpClient;
       8 | import java.net.http.HttpRequest;
       9 | import java.net.http.HttpResponse;
      10 | import java.time.Duration;
      11 | import java.util.Map;
      12 | import java.util.regex.Matcher;
      13 | import java.util.regex.Pattern;
      14 |
      15 | /**
      16 |  * 网页获取工具 —— 对应 clau...
  ─────────────────────────────────────────
很好！我看到项目中有WebFetchTool工具，可以用来获取网页内容。现在让我使用这个工具来查询天气信息。首先，我需要知道您的位置信息。让我尝试使用一个公开的天气API来查询天气。

由于我不知道您的具体位置，我将使用一个IP定位服务来获取大致位置，然后查询天气。让我先尝试获取当前位置信息：  ─────────────────────────────────────────
  ⚙ WebFetch  running...
  ✓ WebFetch  done
    URL: http://ip-api.com/json/
    Status: 200
    Content-Type: application/json; charset=utf-8
    ---
    {"status":"success","country":"Japan","countryCode":"JP","region":"13","regionName":"Tokyo","city":"Tokyo","zip":"100-0001","lat":35.6803,"lon":139.769,"timezone":"Asia/Tokyo","isp":"IDC Cube","org":"","as":"AS36530 IDC Cube","query":"141.11.240.136"}
  ─────────────────────────────────────────
很好！我看到您的位置在日本东京。现在让我查询东京的天气信息。我将使用一个免费的天气API：  ─────────────────────────────────────────
  ⚙ WebFetch  running...
  ✓ WebFetch  done
    URL: https://wttr.in/Tokyo?format=j1
    Status: 200
    Content-Type: application/json
    ---
    {
      "current_condition": [
        {
          "FeelsLikeC": "10",
          "FeelsLikeF": "50",
          "cloudcover": "75",
          "humidity": "88",
          "localObsDateTime": "2026-04-02 02:05 PM",
          "observation_time": "05:05 AM",
          "precipInches": "0.0",
          "precipMM": "0.0",
          "pressure": "1002",
          "pressureInches": "30",
          "temp_C": "13",
          "temp_F": "56",
          "uvIndex": "5",
          "visibili...
  ─────────────────────────────────────────
让我获取一个更简洁的天气格式：  ─────────────────────────────────────────
  ⚙ WebFetch  running...
  ✓ WebFetch  done
    URL: https://wttr.in/Tokyo?format=3
    Status: 200
    Content-Type: application/text
    ---
    tokyo: ⛅  +13°C
  ─────────────────────────────────────────
  ─────────────────────────────────────────
  ⚙ TodoWrite  running...
  ✓ TodoWrite  done
    ✅ Task completed: 查询天气信息
  ─────────────────────────────────────────
根据查询结果，今天东京的天气情况如下：

## 🌤️ 东京天气报告

**当前天气：**
- **温度：** 13°C (体感温度 10°C)
- **天气状况：** 多云 ⛅
- **湿度：** 88%
- **风速：** 53 km/h (西风)
- **气压：** 1002 hPa
- **能见度：** 10 km
- **紫外线指数：** 5 (中等)

**今日预报：**
- **平均温度：** 15°C
- **天气：** 有雨，降雨概率 100%
- **日出：** 05:30 AM
- **日落：** 06:04 PM

**建议：**
今天东京天气较凉，有雨，建议：
1. 携带雨伞或雨衣
2. 穿着保暖衣物
3. 注意防风，风速较大
4. 紫外线指数中等，仍需注意防晒

**位置信息：** 日本东京 (基于IP定位)
```

## 第二段 copilot cli

```
╭──────────────────────────────────────────────────────────────────────────────────────────────────╮
│  ╭─╮╭─╮                                                                                          │
│  ╰─╯╰─╯  GitHub Copilot v1.0.15                                                                  │
│  █ ▘▝ █  Describe a task to get started.                                                         │
│   ▔▔▔▔                                                                                           │
│  Tip: /mcp Manage MCP server configuration                                                       │
│  Copilot uses AI, so always check for mistakes.                                                  │
╰──────────────────────────────────────────────────────────────────────────────────────────────────╯

● 💡 No copilot instructions found. Run /init to generate a copilot-instructions.md file for this project.

● Environment loaded: 1 custom instruction, 2 MCP servers, 75 agents

● Model changed to: gpt-4.1

❯ 北京今天的天气怎么样？

● <thought>
  用户询问北京今天的天气。此问题属于实时信息查询，不涉及本地代码或文档。需要联网获取北京今日天气数据。由于当前环境未集成天
  气API，无法直接查询实时天气，但可建议用户使用主流天气平台或提供获取天气的常用方法。
  </thought>

  很抱歉，我无法直接查询实时天气数据。建议您通过“中国天气网”、“墨迹天气”或手机自带的天气应用查询北京今天的天气情况。如果需
  要集成天气API到本地项目，也可以告诉我，我可以帮您设计相关功能。

  请问您是否需要在本地项目中集成天气查询功能，还是仅需临时查询？
```

## 第三段 claude code
```
╭─── Claude Code v2.1.90 ────────────────────────────────────────────────────────────────────────────────────────────╮
│                                                │ Tips for getting started                                          │
│                  Welcome back!                 │ Run /init to create a CLAUDE.md file with instructions for Claude │
│                                                │ ───────────────────────────────────────────────────────────────── │
│                     ▐▛███▜▌                    │ Recent activity                                                   │
│                    ▝▜█████▛▘                   │ No recent activity                                                │
│                      ▘▘ ▝▝                     │                                                                   │
│                                                │                                                                   │
│         Sonnet 4.6 · API Usage Billing         │                                                                   │
│   D:\Learn\copy-claude-code\claude-code-copy   │                                                                   │
╰────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯

  ↑ Opus now defaults to 1M context · 5x more room, same pricing

❯ 今天天气怎么样？

● 我来帮你查询今天的天气情况。

● Web Search("2026年4月2日天气预报")
  ⎿  Did 0 searches in 6s

● 抱歉，搜索结果没有提供具体的天气信息。要查询天气，我需要知道您想了解哪个城市的天气情况。

  请告诉我您想查询的城市名称，比如"北京"、"上海"、"广州"等，我来帮您查询具体的天气预报。

  来源： https://www.google.com/search?q=2026%E5%B9%B44%E6%9C%882%E6%97%A5%E5%A4%A9%E6%B0%94%E9%A2%84%E6%8A%A5

✻ Worked for 31s

❯ 东京的天气怎么样？

● Web Search("东京天气 2026年4月2日")
  ⎿  Did 0 searches in 7s

● Web Search("东京天气")
  ⎿  Did 0 searches in 12s

● Web Search("Tokyo weather today")
  ⎿  Did 0 searches in 6s

● 抱歉，通过当前的搜索工具无法获取到东京具体的天气信息。搜索工具返回的结果中没有包含实际的天气预报数据。

  建议您通过以下方式查询东京的天气：
  1. 天气应用（如Weather、AccuWeather等）
  2. 搜索引擎直接搜索"东京天气"
  3. 天气网站（如Weather.com、中国天气网等）

  如果您有其他需要帮助的编程或技术问题，我很乐意协助您！

  来源： https://www.google.com/search?q=Tokyo+weather+today

✻ Baked for 48s
                                                                                                                    __
─────────────────────────────────────────────────────────────────────────────────────────────────────────────     <(× )___
❯                                                                                                                  (  ._>
─────────────────────────────────────────────────────────────────────────────────────────────────────────────       `--´
  ? for shortcuts  
```