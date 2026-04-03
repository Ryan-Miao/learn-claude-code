# 上游合并后调整指南

每次与上游仓库（anthropics/claude-code）合并后，Web 项目需要做以下调整。

## 背景

上游项目是 Python 版本的 Claude Code 教程，我们已将其改造为 Java（Spring Boot + Spring AI）。上游更新时可能覆盖我们的改造，需要手动恢复。

## 合并后必做检查清单

### 1. 源码模块：Python → Java

**涉及文件：**

| 文件 | 作用 | 改动要点 |
|------|------|----------|
| `web/scripts/extract-content.ts` | 构建时从源码提取内容 | 改 AGENTS_DIR 指向 Java；改 .py→.java 过滤；改正则匹配 Java 语法 |
| `web/src/components/code/source-viewer.tsx` | 源码查看器语法高亮 | 确保有 `highlightJavaLine` 函数；支持语言自动检测 |
| `web/src/data/generated/versions.json` | 自动生成的版本数据 | 重新运行 extract 脚本即可 |
| `web/src/app/[locale]/page.tsx` | 首页 Hero 代码片段 | 确认显示的是 Java 代码而非 Python |

**验证命令：**

```bash
cd web
npx tsx scripts/extract-content.ts
# 应该看到：Found 12 version directories（而不是 agent files）
# 每个版本文件名应该是 .java（如 S01AgentLoop.java）

# 检查生成的数据
python3 -c "
import json
with open('src/data/generated/versions.json') as f:
    data = json.load(f)
for v in data['versions']:
    print(f\"{v['id']}: {v['filename']}\")
"
# 预期输出：
#   s01: S01AgentLoop.java
#   s02: S02ToolUse.java
#   ...
#   s12: S12WorktreeIsolation.java
```

### 2. GitHub 链接指向

上游的链接指向 `anthropics/claude-code`，需要替换为我们的仓库 `Ryan-Miao/learn-claude-code`。

**涉及文件：**

- `web/src/lib/constants.ts` — GitHub 仓库 URL 常量
- `web/next.config.ts` — 可能包含 GitHub Pages 部署路径
- `web/src/app/[locale]/page.tsx` 或其他页面中的硬编码链接
- `docs/` 下的文档中的链接引用

**检查方法：**

```bash
# 搜索上游仓库引用
grep -r "anthropics/claude-code" web/ --include="*.ts" --include="*.tsx" --include="*.json" -l
grep -r "anthropics/claude-code" docs/ --include="*.md" -l
```

**替换目标：**

```
anthropics/claude-code → Ryan-Miao/learn-claude-code
```

### 3. 构建验证

```bash
cd web
npm run build
```

构建成功后，本地启动验证：

```bash
npx serve out -p 3456
# 浏览器打开 http://localhost:3456/zh
# 检查：首页代码片段是 Java、源码 Tab 显示 .java 文件、无 Python 引用
```

## extract-content.ts 关键改造点

上游版本扫描 `agents/*.py`，我们需要扫描 Java 源码。核心差异：

```
上游（Python）                          我们（Java）
─────────────────────────────────────────────────────────────
AGENTS_DIR = repo/agents/           →   JAVA_SRC_DIR = repo/claude-learn/src/main/java/com/demo/learn/
过滤: *.py                           →   过滤: s01/, s02/, ... 子目录
filenameToVersionId: s01_xxx.py → s01  →   直接用目录名: s01/ → s01
extractClasses: class Xxx:           →   class Xxx { (花括号匹配)
extractFunctions: def foo():         →   method 正则匹配
extractTools: "name": "xxx"          →   @Tool 注解 + "name": "xxx"
countLoc: # 注释                     →   // 和 /* */ 注释
```

## source-viewer.tsx 关键改造点

需要同时支持 Java 和 Python 语法高亮（通过文件名自动检测）：

```tsx
function detectLanguage(filename: string): "java" | "python" {
  if (filename.endsWith(".java")) return "java";
  return "python";
}
```

Java 高亮需要额外支持的关键字：`public`, `private`, `protected`, `class`, `void`, `new`, `var`, `@Override` 等。

## page.tsx 注意事项

首页 Hero 区域的代码片段如果使用 JSX 逐行着色，注意：
- `String... args` 中的 `...` 在 JSX 中需要转义
- `<` 和 `>` 需要用 `&lt;` 和 `&gt;`
- `{` 和 `}` 在 JSX 文本中需要用 `{"{"}` 或模板字符串包裹

**推荐做法：** 使用模板字符串 `{`...`}` 包裹代码文本，然后用 `highlightJavaLine()` 逐行着色，兼顾 Turbopack 兼容性和语法高亮：

```tsx
import { highlightJavaLine } from "@/components/code/source-viewer";

<pre className="... text-zinc-300">
  <code>
    {code.split("\n").map((line, i) => (
      <span key={i}>{highlightJavaLine(line)}{"\n"}</span>
    ))}
  </code>
</pre>
```

**⚠️ 踩坑记录：** 纯模板字符串 `{`code`}` 虽然解决了 JSX 特殊字符问题，但会丢失全部语法高亮，且文字颜色继承 body 导致亮色模式下黑字黑底不可见。必须同时加 `text-zinc-300`（或类似的浅色文字类）和使用 `highlightJavaLine` 着色。

## 快速恢复流程

如果上游合并覆盖了改造，按以下顺序恢复：

```bash
# 1. 合并上游
git fetch upstream
git merge upstream/main

# 2. 修复 extract-content.ts（指向 Java 源码）
# 3. 修复 source-viewer.tsx（Java 语法高亮）
# 4. 修复 page.tsx（首页代码片段）
# 5. 替换 GitHub 链接
grep -r "anthropics/claude-code" --include="*.ts" --include="*.tsx" -rl | xargs sed -i 's|anthropics/claude-code|Ryan-Miao/learn-claude-code|g'

# 6. 重新生成数据并构建
cd web
npx tsx scripts/extract-content.ts
npm run build

# 7. 本地验证
npx serve out -p 3456
```

## 我们的 Java 源码位置

```
claude-learn/src/main/java/com/demo/learn/
├── core/          # 共享核心（AgentRunner, BashTool 等）
├── s01/S01AgentLoop.java
├── s02/S02ToolUse.java
├── s03/S03TodoWrite.java
├── s04/S04Subagent.java
├── s05/S05SkillLoading.java
├── s06/S06ContextCompact.java
├── s07/S07TaskSystem.java
├── s08/S08BackgroundTasks.java
├── s09/S09AgentTeams.java
├── s10/S10TeamProtocols.java
├── s11/S11AutonomousAgents.java
└── s12/S12WorktreeIsolation.java
```

## 历史记录

- 2026-03-23: 上游合并覆盖 Java 改造，恢复 extract-content.ts、source-viewer.tsx、page.tsx
- 2026-03-23: 新建此文档，总结合并调整流程
- 2026-04-04: 首页代码块高亮丢失修复：纯模板字符串会丢高亮+亮色模式黑字黑底，改用 highlightJavaLine() + text-zinc-300
