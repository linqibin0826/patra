#!/bin/bash

# gradle-compile-check.sh
# 目的：对 Patra 多模块项目进行快速 Gradle 编译检查
# 触发器：Stop 事件（仅当 Java 文件被修改时）
# 用法：使用 Gradle 增量编译检测编译错误

set -eo pipefail

# 输出颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # 无颜色

# 导航到项目根目录（假设我们在 .claude/hooks 中）
PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$PROJECT_ROOT" || exit 1

# 检查本次会话中是否修改了 Java 文件
MARKER_FILE="$PROJECT_ROOT/.claude/hooks/.java-files-modified"
if [ ! -f "$MARKER_FILE" ]; then
    echo -e "${BLUE}ℹ️  未检测到 Java 文件更改，跳过 Gradle 编译检查${NC}"
    exit 0
fi

# 移除标记文件（如果再次修改 Java 文件，将重新创建）
rm -f "$MARKER_FILE"

echo "🔨 运行 Gradle 编译检查..."

# 检查 build.gradle.kts 是否存在
if [ ! -f "build.gradle.kts" ]; then
    echo -e "${RED}❌ 错误：项目根目录中未找到 build.gradle.kts${NC}"
    exit 1
fi

# 为编译输出创建临时文件
TEMP_OUTPUT=$(mktemp)
trap 'rm -f "$TEMP_OUTPUT"' EXIT

# 使用 Gradle 编译（利用增量编译和构建缓存）
# --parallel：并行构建
# --build-cache：使用构建缓存
# -q：静默模式（仅错误）
echo "Running: ./gradlew classes testClasses --parallel --build-cache -q"

if ./gradlew classes testClasses --parallel --build-cache -q 2>&1 | tee "$TEMP_OUTPUT"; then
    echo -e "${GREEN}✅ Gradle 编译成功${NC}"
    exit 0
else
    echo -e "${RED}❌ Gradle 编译失败${NC}"
    echo ""
    echo "=== 编译错误摘要 ==="

    # 提取并显示编译错误
    grep -E "error:|cannot find symbol|package .* does not exist|FAILED" "$TEMP_OUTPUT" | head -20

    echo ""
    echo "=== 失败的任务 ==="
    grep -E ":.*FAILED" "$TEMP_OUTPUT" | sed 's/.*\(:[^ ]*\).*/  - \1/' | sort -u

    echo ""
    echo -e "${YELLOW}💡 提示：运行 './gradlew classes testClasses --info' 获取详细的错误信息${NC}"
    echo -e "${YELLOW}💡 或使用自动错误解析器代理自动修复错误${NC}"

    # 为 trigger-build-resolver-java.sh 创建标记文件
    mkdir -p "$PROJECT_ROOT/.claude/hooks"
    touch "$PROJECT_ROOT/.claude/hooks/.last-compile-failed"

    exit 1
fi
