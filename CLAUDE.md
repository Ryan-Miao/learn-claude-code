# CLAUDE.md

## 技术栈

**Java 25 + Spring Boot 4.0 + Spring AI 2.0** 后端 | **Next.js 16 + React 19 + Tailwind CSS v4** 前端 | GitHub Pages 静态部署

## 启动命令

```bash
./dev-backend.sh            # 后端 Web 聊天 (8080)
./dev-backend.sh lesson S01 # 运行课程 S01-S12
./dev-backend.sh build      # 构建
cd web && ./dev.sh          # 前端 (3000)
```

详细命令、环境变量、端口规则见 [DEV.md](./DEV.md)。

## 架构

后端 (`claude-learn/src/main/java/com/demo/learn/`)：12 节独立课程 (s01-s12) 共享 `core/` 基础设施（AiConfig、工具集、AgentRunner、MessageBus）。`web/` 提供 REST API。

前端 (`web/src/`)：App Router + `[locale]` (zh/en/ja) + 静态导出。无组件库/状态管理库，手写 UI。`data/generated/` 由 `scripts/extract-content.ts` 从 `docs/` 自动生成。

关键模式：前端完全静态，动态功能（聊天）通过客户端 fetch 调后端。CSS 自定义属性 5 层颜色系统。国际化回退链：请求语言 → zh → en → key。

## 关键路径

- `web/next.config.ts` — `basePath: "/learn-claude-code"`
- `claude-learn/src/main/resources/application-local.yml` — 本地配置（已 gitignore）
- `.impeccable.md` — 完整设计上下文

## 安全规则

提交前检查暂存文件：禁止包含 API Key、密码、`.env`、`application-local.yml` 等敏感信息。
