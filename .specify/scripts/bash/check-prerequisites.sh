#!/usr/bin/env bash

# 统一的前置条件检查脚本
#
# 该脚本为规范驱动开发工作流提供统一的前置条件检查。
# 它替代了之前分散在多个脚本中的功能。
#
# 用法：./check-prerequisites.sh [选项]
#
# 选项：
#   --json              以 JSON 格式输出
#   --require-tasks     要求 tasks.md 存在（用于实施阶段）
#   --include-tasks     将 tasks.md 包含在 AVAILABLE_DOCS 列表中
#   --paths-only        仅输出路径变量（无验证）
#   --help, -h          显示帮助信息
#
# 输出：
#   JSON 模式：{"FEATURE_DIR":"...", "AVAILABLE_DOCS":["..."]}
#   文本模式：FEATURE_DIR:... \n AVAILABLE_DOCS: \n ✓/✗ file.md
#   仅路径：REPO_ROOT: ... \n BRANCH: ... \n FEATURE_DIR: ... 等

set -e

# 解析命令行参数
JSON_MODE=false
REQUIRE_TASKS=false
INCLUDE_TASKS=false
PATHS_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --json)
            JSON_MODE=true
            ;;
        --require-tasks)
            REQUIRE_TASKS=true
            ;;
        --include-tasks)
            INCLUDE_TASKS=true
            ;;
        --paths-only)
            PATHS_ONLY=true
            ;;
        --help|-h)
            cat << 'EOF'
用法：check-prerequisites.sh [选项]

规范驱动开发工作流的统一前置条件检查。

选项：
  --json              以 JSON 格式输出
  --require-tasks     要求 tasks.md 存在（用于实施阶段）
  --include-tasks     将 tasks.md 包含在 AVAILABLE_DOCS 列表中
  --paths-only        仅输出路径变量（无前置条件验证）
  --help, -h          显示此帮助信息

示例：
  # 检查任务前置条件（需要 plan.md）
  ./check-prerequisites.sh --json

  # 检查实施前置条件（需要 plan.md + tasks.md）
  ./check-prerequisites.sh --json --require-tasks --include-tasks

  # 仅获取特性路径（无验证）
  ./check-prerequisites.sh --paths-only

EOF
            exit 0
            ;;
        *)
            echo "错误：未知选项 '$arg'。使用 --help 获取用法信息。" >&2
            exit 1
            ;;
    esac
done

# 导入公共函数
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# 获取特性路径并验证分支
eval $(get_feature_paths)
check_feature_branch "$CURRENT_BRANCH" "$HAS_GIT" || exit 1

# 如果仅路径模式，输出路径并退出（支持 JSON + 仅路径组合）
if $PATHS_ONLY; then
    if $JSON_MODE; then
        # 最小化的 JSON 路径负载（不执行任何验证）
        printf '{"REPO_ROOT":"%s","BRANCH":"%s","FEATURE_DIR":"%s","FEATURE_SPEC":"%s","IMPL_PLAN":"%s","TASKS":"%s"}\n' \
            "$REPO_ROOT" "$CURRENT_BRANCH" "$FEATURE_DIR" "$FEATURE_SPEC" "$IMPL_PLAN" "$TASKS"
    else
        echo "REPO_ROOT: $REPO_ROOT"
        echo "BRANCH: $CURRENT_BRANCH"
        echo "FEATURE_DIR: $FEATURE_DIR"
        echo "FEATURE_SPEC: $FEATURE_SPEC"
        echo "IMPL_PLAN: $IMPL_PLAN"
        echo "TASKS: $TASKS"
    fi
    exit 0
fi

# 验证必需的目录和文件
if [[ ! -d "$FEATURE_DIR" ]]; then
    echo "错误：找不到特性目录：$FEATURE_DIR" >&2
    echo "请先运行 /speckit.specify 以创建特性结构。" >&2
    exit 1
fi

if [[ ! -f "$IMPL_PLAN" ]]; then
    echo "错误：$FEATURE_DIR 中找不到 plan.md" >&2
    echo "请先运行 /speckit.plan 以创建实施计划。" >&2
    exit 1
fi

# 如果需要，检查 tasks.md
if $REQUIRE_TASKS && [[ ! -f "$TASKS" ]]; then
    echo "错误：$FEATURE_DIR 中找不到 tasks.md" >&2
    echo "请先运行 /speckit.tasks 以创建任务列表。" >&2
    exit 1
fi

# 构建可用文档列表
docs=()

# 始终检查这些可选文档
[[ -f "$RESEARCH" ]] && docs+=("research.md")
[[ -f "$DATA_MODEL" ]] && docs+=("data-model.md")

# 检查合约目录（仅在存在且包含文件时）
if [[ -d "$CONTRACTS_DIR" ]] && [[ -n "$(ls -A "$CONTRACTS_DIR" 2>/dev/null)" ]]; then
    docs+=("contracts/")
fi

[[ -f "$QUICKSTART" ]] && docs+=("quickstart.md")

# 如果请求且存在，则包含 tasks.md
if $INCLUDE_TASKS && [[ -f "$TASKS" ]]; then
    docs+=("tasks.md")
fi

# 输出结果
if $JSON_MODE; then
    # 构建文档的 JSON 数组
    if [[ ${#docs[@]} -eq 0 ]]; then
        json_docs="[]"
    else
        json_docs=$(printf '"%s",' "${docs[@]}")
        json_docs="[${json_docs%,}]"
    fi

    printf '{"FEATURE_DIR":"%s","AVAILABLE_DOCS":%s}\n' "$FEATURE_DIR" "$json_docs"
else
    # 文本输出
    echo "FEATURE_DIR:$FEATURE_DIR"
    echo "AVAILABLE_DOCS:"

    # 显示每个潜在文档的状态
    check_file "$RESEARCH" "research.md"
    check_file "$DATA_MODEL" "data-model.md"
    check_dir "$CONTRACTS_DIR" "contracts/"
    check_file "$QUICKSTART" "quickstart.md"

    if $INCLUDE_TASKS; then
        check_file "$TASKS" "tasks.md"
    fi
fi
