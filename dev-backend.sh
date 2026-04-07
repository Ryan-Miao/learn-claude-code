#!/bin/bash
# 后端启动脚本
# 用法:
#   ./dev-backend.sh              — 启动 Web 聊天后端 (端口 8080)
#   ./dev-backend.sh lesson S01   — 运行指定课程 (S01-S12)
#   ./dev-backend.sh build        — 构建全部

PORT=8080

kill_port() {
  PID=$(lsof -ti:$PORT 2>/dev/null)
  if [ -n "$PID" ]; then
    echo "Killing process on port $PORT (PID: $PID)"
    kill -9 $PID
    sleep 1
  fi
}

run_gradle() {
  local class="$1"
  shift
  ./gradlew :claude-learn:run -PmainClass="$class" "$@"
}

case "${1:-web}" in
  web)
    kill_port
    echo "Starting Web Chat backend..."
    echo ""
    echo "  API:  http://localhost:$PORT/api/chat"
    echo ""
    run_gradle com.demo.learn.web.WebChatApp
    ;;
  lesson)
    LESSON="${2:-S01}"
    LOWER=$(echo "$LESSON" | tr '[:upper:]' '[:lower:]')
    # 自动发现课程类名
    MAIN_CLASS=$(find "claude-learn/src/main/java/com/demo/learn/$LOWER" -name "*.java" -exec grep -l "CommandLineRunner" {} \; 2>/dev/null | head -1 | xargs basename 2>/dev/null | sed 's/\.java$//')
    if [ -z "$MAIN_CLASS" ]; then
      echo "Error: Cannot find lesson $LESSON"
      exit 1
    fi
    echo "Running lesson $LESSON ($MAIN_CLASS) — interactive CLI mode"
    run_gradle "com.demo.learn.$LOWER.$MAIN_CLASS"
    ;;
  build)
    echo "Building all..."
    ./gradlew build
    ;;
  *)
    echo "Usage: $0 [web|lesson SXX|build]"
    exit 1
    ;;
esac
