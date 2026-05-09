#!/bin/bash

# trigger-build-resolver-java.sh
# 目的：当 Gradle 编译失败时提示继续修复
# 说明：Codex 同一事件的多个 hook 会并发执行，不能依赖 Stop hook 顺序。

set -e

# 输出颜色
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # 无颜色

# 检查我们是否在 Java 项目中
if PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    :
else
    PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi

if [ ! -f "$PROJECT_ROOT/build.gradle.kts" ]; then
    # 不是 Gradle 项目，静默退出
    exit 0
fi

# 查找会话中最近的编译错误
# 这是一个简化的检查 - 实际上，你可能想要检查 hook 的退出状态
if [ -f "$PROJECT_ROOT/.codex/hooks/.last-compile-failed" ]; then
    echo -e "${YELLOW}检测到上一次 Gradle 编译失败，建议继续分析并修复编译错误。${NC}" >&2

    # 清理标记文件
    rm -f "$PROJECT_ROOT/.codex/hooks/.last-compile-failed"
fi

printf '{"continue":true}\n'
exit 0
