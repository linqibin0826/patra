#!/bin/bash

# gradle-compile-check.sh
# 目的：对 Patra 多模块项目进行快速 Gradle 编译检查
# 触发器：Codex Stop 事件（仅当 Java 文件被修改时）
# 用法：使用 Gradle 增量编译检测编译错误

set -eo pipefail

# 输出颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # 无颜色

# 导航到项目根目录
if PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    :
else
    PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
cd "$PROJECT_ROOT" || exit 1

# 检查本次会话中是否修改了 Java 文件
MARKER_FILE="$PROJECT_ROOT/.codex/hooks/.java-files-modified"
if [ ! -f "$MARKER_FILE" ]; then
    echo -e "${BLUE}未检测到 Java 文件更改，跳过 Gradle 编译检查${NC}" >&2
    printf '{"continue":true}\n'
    exit 0
fi

# 移除标记文件（如果再次修改 Java 文件，将重新创建）
rm -f "$MARKER_FILE"

echo "运行 Gradle 编译检查..." >&2

# 检查 build.gradle.kts 是否存在
if [ ! -f "build.gradle.kts" ]; then
    echo -e "${RED}错误：项目根目录中未找到 build.gradle.kts${NC}" >&2
    printf '{"decision":"block","reason":"项目根目录中未找到 build.gradle.kts，无法完成 Stop hook 编译检查。"}\n'
    exit 0
fi

# 为编译输出创建临时文件
TEMP_OUTPUT=$(mktemp)
trap 'rm -f "$TEMP_OUTPUT"' EXIT

# 使用 Gradle 编译（利用增量编译和构建缓存）
# --parallel：并行构建
# --build-cache：使用构建缓存
# -q：静默模式（仅错误）
echo "Running: ./gradlew classes testClasses --parallel --build-cache -q" >&2

if ./gradlew classes testClasses --parallel --build-cache -q 2>&1 | tee "$TEMP_OUTPUT" >&2; then
    echo -e "${GREEN}Gradle 编译成功${NC}" >&2
    printf '{"continue":true}\n'
    exit 0
else
    echo -e "${RED}Gradle 编译失败${NC}" >&2
    echo "" >&2
    echo "=== 编译错误摘要 ===" >&2

    # 提取并显示编译错误
    grep -E "error:|cannot find symbol|package .* does not exist|FAILED" "$TEMP_OUTPUT" | head -20 >&2 || true

    echo "" >&2
    echo "=== 失败的任务 ===" >&2
    grep -E ":.*FAILED" "$TEMP_OUTPUT" | sed 's/.*\(:[^ ]*\).*/  - \1/' | sort -u >&2 || true

    echo "" >&2
    echo -e "${YELLOW}提示：运行 './gradlew classes testClasses --info' 获取详细错误信息${NC}" >&2
    echo -e "${YELLOW}建议继续修复编译错误后再次验证${NC}" >&2

    # 为 trigger-build-resolver-java.sh 创建标记文件
    mkdir -p "$PROJECT_ROOT/.codex/hooks"
    touch "$PROJECT_ROOT/.codex/hooks/.last-compile-failed"

    printf '{"decision":"block","reason":"Gradle 编译失败。请根据 Stop hook 输出继续修复编译错误，然后重新运行验证。"}\n'
    exit 0
fi
