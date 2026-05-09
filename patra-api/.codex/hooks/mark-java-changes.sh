#!/bin/bash

# mark-java-changes.sh
# 目的：标记通过 Codex 文件编辑工具修改 Java 文件的时刻
# 触发器：PostToolUse（Edit/Write/apply_patch）
# 用法：设置一个标志，gradle-compile-check.sh 将检查

set -euo pipefail

HOOK_INPUT=$(cat)
if PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    :
else
    PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
MARKER_DIR="$PROJECT_ROOT/.codex/hooks"

# 检查它是否是 Java 文件
if printf '%s' "$HOOK_INPUT" | grep -qE '\.java(["[:space:]]|$)'; then
    mkdir -p "$MARKER_DIR"
    touch "$MARKER_DIR/.java-files-modified"
fi

# 非阻塞 hook：始终成功退出
exit 0
