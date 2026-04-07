#!/bin/bash
# 前端开发服务器重启脚本
# 用法: ./dev.sh          — 杀掉占用端口的进程后启动
#       ./dev.sh --keep   — 不杀进程，直接启动（如果端口空闲）

PORT=3000

if [ "$1" != "--keep" ]; then
  PID=$(lsof -ti:$PORT 2>/dev/null)
  if [ -n "$PID" ]; then
    echo "Killing process on port $PORT (PID: $PID)"
    kill -9 $PID
    sleep 1
  else
    echo "Port $PORT is free"
  fi
fi

echo ""
echo "  Frontend ready → http://localhost:$PORT/learn-claude-code/zh"
echo ""
npm run dev
