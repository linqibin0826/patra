#!/bin/bash

# auto-format-java.sh
# 目的：使用 Google Java Format 在 Edit/Write 后自动格式化 Java 文件
# 触发器：PostToolUse（在 Edit/Write/MultiEdit 后）
# 用法：在修改的文件上运行 fmt:format

set -e

# 输出颜色
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # 无颜色

# 从工具输入的 stdin 中读取文件路径
FILE_PATH=$(cat | jq -r '.tool_input.file_path // empty' 2>/dev/null)

# 检查它是否是 Java 文件
if [[ "$FILE_PATH" == *.java ]]; then
    PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"

    # 检查文件是否存在且在项目中
    if [[ -f "$FILE_PATH" ]]; then
        echo -e "${BLUE}🎨 格式化 Java 文件：$(basename "$FILE_PATH")${NC}"

        # 在特定文件上运行 fmt:format
        # 使用 -q 进行静默输出，仅显示错误
        if cd "$PROJECT_ROOT" && mvn -q fmt:format -Dformat.files="$FILE_PATH" 2>/dev/null; then
            echo -e "${GREEN}✅ 格式化应用成功${NC}"
        else
            # 如果特定文件格式化失败，尝试格式化整个模块
            # 这是一个备用方案，fmt:format 在模块级别工作
            MODULE_DIR=$(dirname "$FILE_PATH")
            while [[ "$MODULE_DIR" != "$PROJECT_ROOT" ]] && [[ ! -f "$MODULE_DIR/pom.xml" ]]; do
                MODULE_DIR=$(dirname "$MODULE_DIR")
            done

            if [[ -f "$MODULE_DIR/pom.xml" ]]; then
                echo -e "${BLUE}🔄 格式化模块：$(basename "$MODULE_DIR")${NC}"
                cd "$MODULE_DIR" && mvn -q fmt:format 2>/dev/null || true
            fi
        fi
    fi
fi

# 非阻塞 hook：始终成功退出
exit 0
