#!/usr/bin/env bash
# 所有脚本的通用函数和变量

# 获取仓库根目录，对于非 git 仓库提供回退方案
get_repo_root() {
    if git rev-parse --show-toplevel >/dev/null 2>&1; then
        git rev-parse --show-toplevel
    else
        # 对于非 git 仓库，回退到脚本位置
        local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        (cd "$script_dir/../../.." && pwd)
    fi
}

# 获取当前分支，对于非 git 仓库提供回退方案
get_current_branch() {
    # 首先检查是否设置了 SPECIFY_FEATURE 环境变量
    if [[ -n "${SPECIFY_FEATURE:-}" ]]; then
        echo "$SPECIFY_FEATURE"
        return
    fi

    # 然后检查 git（如果可用）
    if git rev-parse --abbrev-ref HEAD >/dev/null 2>&1; then
        git rev-parse --abbrev-ref HEAD
        return
    fi

    # 对于非 git 仓库，尝试查找最新的特性目录
    local repo_root=$(get_repo_root)
    local specs_dir="$repo_root/specs"

    if [[ -d "$specs_dir" ]]; then
        local latest_feature=""
        local highest=0

        for dir in "$specs_dir"/*; do
            if [[ -d "$dir" ]]; then
                local dirname=$(basename "$dir")
                if [[ "$dirname" =~ ^([0-9]{3})- ]]; then
                    local number=${BASH_REMATCH[1]}
                    number=$((10#$number))
                    if [[ "$number" -gt "$highest" ]]; then
                        highest=$number
                        latest_feature=$dirname
                    fi
                fi
            fi
        done

        if [[ -n "$latest_feature" ]]; then
            echo "$latest_feature"
            return
        fi
    fi

    echo "main"  # 最终回退
}

# 检查是否有可用的 git
has_git() {
    git rev-parse --show-toplevel >/dev/null 2>&1
}

check_feature_branch() {
    local branch="$1"
    local has_git_repo="$2"

    # 对于非 git 仓库，我们无法强制执行分支命名，但仍提供输出
    if [[ "$has_git_repo" != "true" ]]; then
        echo "[specify] 警告：未检测到 Git 仓库；跳过分支验证" >&2
        return 0
    fi

    if [[ ! "$branch" =~ ^[0-9]{3}- ]]; then
        echo "错误：不在特性分支上。当前分支：$branch" >&2
        echo "特性分支应该像这样命名：001-feature-name" >&2
        return 1
    fi

    return 0
}

get_feature_dir() { echo "$1/specs/$2"; }

# 通过数字前缀查找特性目录，而不是精确的分支匹配
# 这允许多个分支处理同一规格说明（例如，004-fix-bug、004-add-feature）
find_feature_dir_by_prefix() {
    local repo_root="$1"
    local branch_name="$2"
    local specs_dir="$repo_root/specs"

    # 从分支中提取数字前缀（例如，从 "004-whatever" 中提取 "004"）
    if [[ ! "$branch_name" =~ ^([0-9]{3})- ]]; then
        # 如果分支没有数字前缀，回退到精确匹配
        echo "$specs_dir/$branch_name"
        return
    fi

    local prefix="${BASH_REMATCH[1]}"

    # 在 specs/ 中搜索以此前缀开头的目录
    local matches=()
    if [[ -d "$specs_dir" ]]; then
        for dir in "$specs_dir"/"$prefix"-*; do
            if [[ -d "$dir" ]]; then
                matches+=("$(basename "$dir")")
            fi
        done
    fi

    # 处理结果
    if [[ ${#matches[@]} -eq 0 ]]; then
        # 未找到匹配 - 返回分支名称路径（稍后将失败并显示清晰错误）
        echo "$specs_dir/$branch_name"
    elif [[ ${#matches[@]} -eq 1 ]]; then
        # 恰好一个匹配 - 完美！
        echo "$specs_dir/${matches[0]}"
    else
        # 多个匹配 - 使用正确的命名约定不应发生这种情况
        echo "错误：找到多个前缀为 '$prefix' 的规格说明目录：${matches[*]}" >&2
        echo "请确保每个数字前缀只有一个规格说明目录。" >&2
        echo "$specs_dir/$branch_name"  # 返回一些内容以避免破坏脚本
    fi
}

get_feature_paths() {
    local repo_root=$(get_repo_root)
    local current_branch=$(get_current_branch)
    local has_git_repo="false"

    if has_git; then
        has_git_repo="true"
    fi

    # 使用基于前缀的查找来支持每个规格说明的多个分支
    local feature_dir=$(find_feature_dir_by_prefix "$repo_root" "$current_branch")

    cat <<EOF
REPO_ROOT='$repo_root'
CURRENT_BRANCH='$current_branch'
HAS_GIT='$has_git_repo'
FEATURE_DIR='$feature_dir'
FEATURE_SPEC='$feature_dir/spec.md'
IMPL_PLAN='$feature_dir/plan.md'
TASKS='$feature_dir/tasks.md'
RESEARCH='$feature_dir/research.md'
DATA_MODEL='$feature_dir/data-model.md'
QUICKSTART='$feature_dir/quickstart.md'
CONTRACTS_DIR='$feature_dir/contracts'
EOF
}

check_file() { [[ -f "$1" ]] && echo "  ✓ $2" || echo "  ✗ $2"; }
check_dir() { [[ -d "$1" && -n $(ls -A "$1" 2>/dev/null) ]] && echo "  ✓ $2" || echo "  ✗ $2"; }
