#!/bin/bash

# mark-java-changes.sh
# Purpose: Mark when Java files are modified via Edit/Write/MultiEdit
# Triggers on: PostToolUse (after Edit/Write/MultiEdit)
# Usage: Sets a flag that maven-compile-check.sh will check

# Read stdin to get file path from tool input
FILE_PATH=$(cat | jq -r '.tool_input.file_path // empty' 2>/dev/null)

# Check if it's a Java file
if [[ "$FILE_PATH" == *.java ]]; then
    PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
    mkdir -p "$PROJECT_ROOT/.claude/hooks"
    touch "$PROJECT_ROOT/.claude/hooks/.java-files-modified"
fi

# Non-blocking hook: always exit successfully
exit 0
