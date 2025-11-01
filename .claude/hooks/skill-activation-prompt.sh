#!/bin/bash

# Non-blocking hook: should always exit 0 per Claude Code best practices
cd "$CLAUDE_PROJECT_DIR/.claude/hooks" || exit 0

# Use local tsx binary for better performance
if [ -x "./node_modules/.bin/tsx" ]; then
    cat | ./node_modules/.bin/tsx skill-activation-prompt.ts || exit 0
else
    # Fallback to npx if local binary not found
    cat | npx tsx skill-activation-prompt.ts || exit 0
fi
