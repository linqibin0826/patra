#!/bin/bash

# send-stop-notification.sh
# 目的：在 Codex 停止时发送系统通知
# 触发器：Codex Stop 事件
# 平台：macOS (使用 osascript)

set -eo pipefail

# 项目根目录
if PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    :
else
    PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
PROJECT_NAME=$(basename "$PROJECT_ROOT")

# 获取当前分支
if command -v git &> /dev/null && git rev-parse --git-dir > /dev/null 2>&1; then
    GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
else
    GIT_BRANCH="unknown"
fi

# 构建通知消息
TITLE="Codex 已停止"
MESSAGE="项目: $PROJECT_NAME
分支: $GIT_BRANCH"

# 发送 macOS 系统通知
osascript -e "display notification \"$MESSAGE\" with title \"$TITLE\" sound name \"Glass\"" 2>/dev/null

printf '{"continue":true}\n'
exit 0
