#!/usr/bin/env bash

set -e

JSON_MODE=false
SHORT_NAME=""
BRANCH_NUMBER=""
ARGS=()
i=1
while [ $i -le $# ]; do
    arg="${!i}"
    case "$arg" in
        --json)
            JSON_MODE=true
            ;;
        --short-name)
            if [ $((i + 1)) -gt $# ]; then
                echo '错误: --short-name 需要一个值' >&2
                exit 1
            fi
            i=$((i + 1))
            next_arg="${!i}"
            # 检查下一个参数是否为另一个选项（以 -- 开头）
            if [[ "$next_arg" == --* ]]; then
                echo '错误: --short-name 需要一个值' >&2
                exit 1
            fi
            SHORT_NAME="$next_arg"
            ;;
        --number)
            if [ $((i + 1)) -gt $# ]; then
                echo '错误: --number 需要一个值' >&2
                exit 1
            fi
            i=$((i + 1))
            next_arg="${!i}"
            if [[ "$next_arg" == --* ]]; then
                echo '错误: --number 需要一个值' >&2
                exit 1
            fi
            BRANCH_NUMBER="$next_arg"
            ;;
        --help|-h)
            echo "用法: $0 [--json] [--short-name <名称>] [--number N] <功能描述>"
            echo ""
            echo "选项:"
            echo "  --json              以 JSON 格式输出"
            echo "  --short-name <名称> 为分支提供自定义短名称（2-4 个单词）"
            echo "  --number N          手动指定分支编号（覆盖自动检测）"
            echo "  --help, -h          显示此帮助信息"
            echo ""
            echo "示例:"
            echo "  $0 'Add user authentication system' --short-name 'user-auth'"
            echo "  $0 'Implement OAuth2 integration for API' --number 5"
            exit 0
            ;;
        *)
            ARGS+=("$arg")
            ;;
    esac
    i=$((i + 1))
done

FEATURE_DESCRIPTION="${ARGS[*]}"
if [ -z "$FEATURE_DESCRIPTION" ]; then
    echo "用法: $0 [--json] [--short-name <名称>] [--number N] <功能描述>" >&2
    exit 1
fi

# 通过搜索现有项目标记来查找存储库根目录的函数
find_repo_root() {
    local dir="$1"
    while [ "$dir" != "/" ]; do
        if [ -d "$dir/.git" ] || [ -d "$dir/.specify" ]; then
            echo "$dir"
            return 0
        fi
        dir="$(dirname "$dir")"
    done
    return 1
}

# 检查现有分支（本地和远程）并返回下一个可用编号的函数
check_existing_branches() {
    local short_name="$1"

    # 获取所有远程以获得最新分支信息（如果没有远程则压制错误）
    git fetch --all --prune 2>/dev/null || true

    # 使用 git ls-remote 查找所有匹配模式的分支（更可靠）
    local remote_branches=$(git ls-remote --heads origin 2>/dev/null | grep -E "refs/heads/[0-9]+-${short_name}$" | sed 's/.*\/\([0-9]*\)-.*/\1/' | sort -n)

    # 同时检查本地分支
    local local_branches=$(git branch 2>/dev/null | grep -E "^[* ]*[0-9]+-${short_name}$" | sed 's/^[* ]*//' | sed 's/-.*//' | sort -n)

    # 也检查 specs 目录
    local spec_dirs=""
    if [ -d "$SPECS_DIR" ]; then
        spec_dirs=$(find "$SPECS_DIR" -maxdepth 1 -type d -name "[0-9]*-${short_name}" 2>/dev/null | xargs -n1 basename 2>/dev/null | sed 's/-.*//' | sort -n)
    fi

    # 合并所有来源并获取最高编号
    local max_num=0
    for num in $remote_branches $local_branches $spec_dirs; do
        if [ "$num" -gt "$max_num" ]; then
            max_num=$num
        fi
    done

    # 返回下一个编号
    echo $((max_num + 1))
}

# 解析存储库根目录。在可用时优先使用 git 信息，但如果使用
# --no-git 初始化了存储库，则回退到搜索存储库标记，
# 以便工作流仍然能够正常运行。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if git rev-parse --show-toplevel >/dev/null 2>&1; then
    REPO_ROOT=$(git rev-parse --show-toplevel)
    HAS_GIT=true
else
    REPO_ROOT="$(find_repo_root "$SCRIPT_DIR")"
    if [ -z "$REPO_ROOT" ]; then
        echo "错误: 无法确定存储库根目录。请从存储库内运行此脚本。" >&2
        exit 1
    fi
    HAS_GIT=false
fi

cd "$REPO_ROOT"

SPECS_DIR="$REPO_ROOT/specs"
mkdir -p "$SPECS_DIR"

# 带有停用词过滤和长度过滤的生成分支名称的函数
generate_branch_name() {
    local description="$1"

    # 要过滤的常见停用词
    local stop_words="^(i|a|an|the|to|for|of|in|on|at|by|with|from|is|are|was|were|be|been|being|have|has|had|do|does|did|will|would|should|could|can|may|might|must|shall|this|that|these|those|my|your|our|their|want|need|add|get|set)$"

    # 转换为小写并拆分为单词
    local clean_name=$(echo "$description" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/ /g')

    # 过滤单词：删除停用词和长度短于 3 个字符的单词（除非它们在原文中是大写首字母缩略词）
    local meaningful_words=()
    for word in $clean_name; do
        # 跳过空单词
        [ -z "$word" ] && continue

        # 保留既不是停用词且（长度 >= 3 或是潜在的首字母缩略词）的单词
        if ! echo "$word" | grep -qiE "$stop_words"; then
            if [ ${#word} -ge 3 ]; then
                meaningful_words+=("$word")
            elif echo "$description" | grep -q "\b${word^^}\b"; then
                # 如果短单词在原文中显示为大写，则保留（可能是首字母缩略词）
                meaningful_words+=("$word")
            fi
        fi
    done

    # 如果我们有有意义的单词，使用前 3-4 个
    if [ ${#meaningful_words[@]} -gt 0 ]; then
        local max_words=3
        if [ ${#meaningful_words[@]} -eq 4 ]; then max_words=4; fi

        local result=""
        local count=0
        for word in "${meaningful_words[@]}"; do
            if [ $count -ge $max_words ]; then break; fi
            if [ -n "$result" ]; then result="$result-"; fi
            result="$result$word"
            count=$((count + 1))
        done
        echo "$result"
    else
        # 如果找不到有意义的单词，回退到原始逻辑
        echo "$description" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g' | sed 's/-\+/-/g' | sed 's/^-//' | sed 's/-$//' | tr '-' '\n' | grep -v '^$' | head -3 | tr '\n' '-' | sed 's/-$//'
    fi
}

# 生成分支名称
if [ -n "$SHORT_NAME" ]; then
    # 使用提供的短名称，只需清理一下
    BRANCH_SUFFIX=$(echo "$SHORT_NAME" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g' | sed 's/-\+/-/g' | sed 's/^-//' | sed 's/-$//')
else
    # 从描述生成，使用智能过滤
    BRANCH_SUFFIX=$(generate_branch_name "$FEATURE_DESCRIPTION")
fi

# 确定分支编号
if [ -z "$BRANCH_NUMBER" ]; then
    if [ "$HAS_GIT" = true ]; then
        # 检查远程上的现有分支
        BRANCH_NUMBER=$(check_existing_branches "$BRANCH_SUFFIX")
    else
        # 回退到本地目录检查
        HIGHEST=0
        if [ -d "$SPECS_DIR" ]; then
            for dir in "$SPECS_DIR"/*; do
                [ -d "$dir" ] || continue
                dirname=$(basename "$dir")
                number=$(echo "$dirname" | grep -o '^[0-9]\+' || echo "0")
                number=$((10#$number))
                if [ "$number" -gt "$HIGHEST" ]; then HIGHEST=$number; fi
            done
        fi
        BRANCH_NUMBER=$((HIGHEST + 1))
    fi
fi

FEATURE_NUM=$(printf "%03d" "$BRANCH_NUMBER")
BRANCH_NAME="${FEATURE_NUM}-${BRANCH_SUFFIX}"

# GitHub 对分支名称执行 244 字节的限制
# 验证并在必要时截断
MAX_BRANCH_LENGTH=244
if [ ${#BRANCH_NAME} -gt $MAX_BRANCH_LENGTH ]; then
    # 计算我们需要从后缀中删除多少
    # 考虑：功能编号 (3) + 连字符 (1) = 4 个字符
    MAX_SUFFIX_LENGTH=$((MAX_BRANCH_LENGTH - 4))

    # 如果可能，在单词边界处截断后缀
    TRUNCATED_SUFFIX=$(echo "$BRANCH_SUFFIX" | cut -c1-$MAX_SUFFIX_LENGTH)
    # 如果截断产生了后缀连字符，则删除
    TRUNCATED_SUFFIX=$(echo "$TRUNCATED_SUFFIX" | sed 's/-$//')

    ORIGINAL_BRANCH_NAME="$BRANCH_NAME"
    BRANCH_NAME="${FEATURE_NUM}-${TRUNCATED_SUFFIX}"

    >&2 echo "[specify] 警告: 分支名称超过了 GitHub 的 244 字节限制"
    >&2 echo "[specify] 原始: $ORIGINAL_BRANCH_NAME (${#ORIGINAL_BRANCH_NAME} 字节)"
    >&2 echo "[specify] 截断为: $BRANCH_NAME (${#BRANCH_NAME} 字节)"
fi

if [ "$HAS_GIT" = true ]; then
    git checkout -b "$BRANCH_NAME"
else
    >&2 echo "[specify] 警告: 未检测到 Git 存储库; 跳过了为 $BRANCH_NAME 创建分支"
fi

FEATURE_DIR="$SPECS_DIR/$BRANCH_NAME"
mkdir -p "$FEATURE_DIR"

TEMPLATE="$REPO_ROOT/.specify/templates/spec-template.md"
SPEC_FILE="$FEATURE_DIR/spec.md"
if [ -f "$TEMPLATE" ]; then cp "$TEMPLATE" "$SPEC_FILE"; else touch "$SPEC_FILE"; fi

# 为当前会话设置 SPECIFY_FEATURE 环境变量
export SPECIFY_FEATURE="$BRANCH_NAME"

if $JSON_MODE; then
    printf '{"BRANCH_NAME":"%s","SPEC_FILE":"%s","FEATURE_NUM":"%s"}\n' "$BRANCH_NAME" "$SPEC_FILE" "$FEATURE_NUM"
else
    echo "BRANCH_NAME: $BRANCH_NAME"
    echo "SPEC_FILE: $SPEC_FILE"
    echo "FEATURE_NUM: $FEATURE_NUM"
    echo "SPECIFY_FEATURE 环境变量设置为: $BRANCH_NAME"
fi
