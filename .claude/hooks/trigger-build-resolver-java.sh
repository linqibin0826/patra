#!/bin/bash

# trigger-build-resolver-java.sh
# 目的：当 Maven 编译失败时自动建议使用自动错误解析器代理
# 触发器：Stop 事件（在 maven-compile-check.sh 之后）
# 依赖关系：maven-compile-check.sh 必须首先运行

set -e

# 输出颜色
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # 无颜色

# 检查是否有编译错误指示器
# 此脚本应在 maven-compile-check.sh 之后运行
# 如果前一个 hook 失败（编译错误），建议使用代理

# 检查我们是否在 Java 项目中
PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"

if [ ! -f "$PROJECT_ROOT/pom.xml" ]; then
    # 不是 Maven 项目，静默退出
    exit 0
fi

# 查找会话中最近的编译错误
# 这是一个简化的检查 - 实际上，你可能想要检查 hook 的退出状态
if [ -f "$PROJECT_ROOT/.claude/hooks/.last-compile-failed" ]; then
    echo ""
    echo -e "${YELLOW}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${YELLOW}║ 🤖 自动错误解析器代理可用                                   ║${NC}"
    echo -e "${YELLOW}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${CYAN}你想让我使用自动错误解析器代理来修复这些编译错误吗？${NC}"
    echo ""
    echo "代理将："
    echo "  1. 分析编译错误"
    echo "  2. 识别根本原因"
    echo "  3. 自动应用修复"
    echo "  4. 重新运行编译以验证"
    echo ""

    # 清理标记文件
    rm -f "$PROJECT_ROOT/.claude/hooks/.last-compile-failed"
fi

exit 0
