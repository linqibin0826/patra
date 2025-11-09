#!/usr/bin/env bash

set -e

# 解析命令行参数
JSON_MODE=false
ARGS=()

for arg in "$@"; do
    case "$arg" in
        --json)
            JSON_MODE=true
            ;;
        --help|-h)
            echo "用法: $0 [--json]"
            echo "  --json    以 JSON 格式输出结果"
            echo "  --help    显示此帮助信息"
            exit 0
            ;;
        *)
            ARGS+=("$arg")
            ;;
    esac
done

# 获取脚本目录并加载公共函数
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# 从公共函数获取所有路径和变量
eval $(get_feature_paths)

# 检查是否在正确的特性分支上（仅适用于 git 仓库）
check_feature_branch "$CURRENT_BRANCH" "$HAS_GIT" || exit 1

# 确保特性目录存在
mkdir -p "$FEATURE_DIR"

# 复制计划模板（如果存在）
TEMPLATE="$REPO_ROOT/.specify/templates/plan-template.md"
if [[ -f "$TEMPLATE" ]]; then
    cp "$TEMPLATE" "$IMPL_PLAN"
    echo "已将计划模板复制到 $IMPL_PLAN"
else
    echo "警告：在 $TEMPLATE 中未找到计划模板"
    # 如果模板不存在，创建一个基本计划文件
    touch "$IMPL_PLAN"
fi

# 输出结果
if $JSON_MODE; then
    printf '{"FEATURE_SPEC":"%s","IMPL_PLAN":"%s","SPECS_DIR":"%s","BRANCH":"%s","HAS_GIT":"%s"}\n' \
        "$FEATURE_SPEC" "$IMPL_PLAN" "$FEATURE_DIR" "$CURRENT_BRANCH" "$HAS_GIT"
else
    echo "FEATURE_SPEC: $FEATURE_SPEC"
    echo "IMPL_PLAN: $IMPL_PLAN"
    echo "SPECS_DIR: $FEATURE_DIR"
    echo "BRANCH: $CURRENT_BRANCH"
    echo "HAS_GIT: $HAS_GIT"
fi
