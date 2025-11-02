#!/bin/bash

# 非阻塞 hook：按照 Claude Code 最佳实践，应始终返回 0
cd "$CLAUDE_PROJECT_DIR/.claude/hooks" || exit 0

# 使用本地 tsx 二进制文件以获得更好的性能
if [ -x "./node_modules/.bin/tsx" ]; then
    cat | ./node_modules/.bin/tsx skill-activation-prompt.ts || exit 0
else
    # 如果未找到本地二进制文件，回退到 npx
    cat | npx tsx skill-activation-prompt.ts || exit 0
fi
