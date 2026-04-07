# 开发指南

详细开发与调试文档。CLAUDE.md 仅保留精简摘要，本文档供深入参考。

## 快速启动

### 全栈开发（前端 + 后端）

```bash
# 终端 1：启动后端
./dev-backend.sh
# → API 地址：http://localhost:8080/api/chat

# 终端 2：启动前端
cd web && ./dev.sh
# → 首页地址：http://localhost:3000/learn-claude-code/zh
```

### 仅运行课程（CLI 模式，无需前端）

```bash
./dev-backend.sh lesson S01   # S01-S12
# → 交互式终端，无需浏览器
```

### 构建

```bash
./dev-backend.sh build        # 后端构建
cd web && npm run build       # 前端静态导出到 web/out/
```

## 访问地址速查

| 服务 | 地址 |
|------|------|
| 前端首页 | `http://localhost:3000/learn-claude-code/zh` |
| 前端英文 | `http://localhost:3000/learn-claude-code/en` |
| 前端日文 | `http://localhost:3000/learn-claude-code/ja` |
| 后端 API | `http://localhost:8080/api/chat` |

注意：`http://localhost:3000` 和 `http://localhost:3000/learn-claude-code` 会 404，必须带 locale 路径。

## 环境变量

- `AI_API_KEY` — API 密钥
- `AI_BASE_URL` — API 基础 URL
- `AI_MODEL` — 模型名称
- `NEXT_PUBLIC_API_URL` — 前端连接后端地址（默认 `http://192.168.3.7:8080`）

本地覆盖配置：`claude-learn/src/main/resources/application-local.yml`（已 gitignore）

## 详细命令

```bash
# 后端
./gradlew build                                                              # 构建
./gradlew :claude-learn:run -PmainClass=com.demo.learn.web.WebChatApp        # Web 后端
./gradlew :claude-learn:run -PmainClass=com.demo.learn.s01.S01AgentLoop      # 课程
./gradlew :claude-learn:run -PmainClass=com.demo.learn.s01.S01AgentLoop -PanthropicLog=debug  # 调试

# 前端
cd web
npm install       # 安装依赖（extract 作为 preinstall 钩子自动运行）
./dev.sh          # 重启（自动 kill 端口 3000 占用）
./dev.sh --keep   # 不 kill，直接启动
npm run dev       # 直接启动
npx tsc --noEmit  # 仅类型检查
```

## 端口规则

- 端口被占用时：`lsof -ti:3000 | xargs kill -9`
- **禁止** `killall node` / `pkill node`（会杀掉 MCP server 等其他进程）
- kill 后必须在原端口重启，**不要换端口**
