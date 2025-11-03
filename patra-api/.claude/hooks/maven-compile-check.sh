#!/bin/bash

# maven-compile-check.sh
# 目的：对 Papertrace 多模块项目进行快速 Maven 编译检查
# 触发器：Stop 事件（仅当 Java 文件被修改时）
# 用法：使用多线程运行 mvn compile 以快速检测编译错误

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
    echo -e "${BLUE}ℹ️  未检测到 Java 文件更改，跳过 Maven 编译检查${NC}"
    exit 0
fi

# 移除标记文件（如果再次修改 Java 文件，将重新创建）
rm -f "$MARKER_FILE"

echo "🔨 运行 Maven 编译检查..."

# 检查 pom.xml 是否存在
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}❌ 错误：项目根目录中未找到 pom.xml${NC}"
    exit 1
fi

# 为编译输出创建临时文件
TEMP_OUTPUT=$(mktemp)
trap 'rm -f "$TEMP_OUTPUT"' EXIT

# 使用多线程运行 Maven 编译
# -T 2C：每个 CPU 核心使用 2 个线程（基于性能测试的最优值）
# -q：静默模式（仅错误）
# -DskipTests：跳过测试编译和执行
echo "Running: ./mvnw -T 2C clean compile -q -DskipTests"

if ./mvnw -T 2C clean compile -q -DskipTests 2>&1 | tee "$TEMP_OUTPUT"; then
    echo -e "${GREEN}✅ Maven 编译成功${NC}"
    exit 0
else
    echo -e "${RED}❌ Maven 编译失败${NC}"
    echo ""
    echo "=== 编译错误摘要 ==="

    # 提取并显示编译错误
    grep -E "\[ERROR\]|error:|cannot find symbol|package .* does not exist" "$TEMP_OUTPUT" | head -20

    echo ""
    echo "=== 失败的模块 ==="
    grep -E "BUILD FAILURE|FAILURE \[" "$TEMP_OUTPUT" | sed 's/.*\[\(.*\)\].*/  - \1/' | sort -u

    echo ""
    echo -e "${YELLOW}💡 提示：运行 './mvnw clean compile' 获取详细的错误信息${NC}"
    echo -e "${YELLOW}💡 或使用自动错误解析器代理自动修复错误${NC}"

    # 为 trigger-build-resolver-java.sh 创建标记文件
    mkdir -p "$PROJECT_ROOT/.claude/hooks"
    touch "$PROJECT_ROOT/.claude/hooks/.last-compile-failed"

    exit 1
fi
