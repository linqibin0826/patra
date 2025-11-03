#!/bin/bash
# 纯 Shell 实现的技能激活提示 hook
# 依赖: jq (JSON 处理器)

set -e

# 非阻塞 hook:总是返回 0
trap 'exit 0' ERR

# 从 stdin 读取 JSON 输入
INPUT=$(cat)

# 提取 prompt 字段并转为小写
PROMPT=$(echo "$INPUT" | jq -r '.prompt // ""' | tr '[:upper:]' '[:lower:]')

# 如果 prompt 为空,静默退出
if [ -z "$PROMPT" ]; then
    exit 0
fi

# 加载技能规则
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$HOME/project}"
RULES_FILE="$PROJECT_DIR/.claude/skills/skill-rules.json"

# 如果规则文件不存在,静默退出
if [ ! -f "$RULES_FILE" ]; then
    exit 0
fi

# 匹配的技能数组
MATCHED_SKILLS=()
MATCHED_PRIORITIES=()

# 读取所有技能名称
SKILL_NAMES=$(jq -r '.skills | keys[]' "$RULES_FILE")

# 检查每个技能
while IFS= read -r SKILL_NAME; do
    # 提取关键字列表
    KEYWORDS=$(jq -r ".skills[\"$SKILL_NAME\"].promptTriggers.keywords[]? // empty" "$RULES_FILE" 2>/dev/null || true)

    # 检查关键字匹配
    MATCHED=false
    while IFS= read -r KEYWORD; do
        [ -z "$KEYWORD" ] && continue
        KEYWORD_LOWER=$(echo "$KEYWORD" | tr '[:upper:]' '[:lower:]')
        if echo "$PROMPT" | grep -qF "$KEYWORD_LOWER"; then
            MATCHED=true
            break
        fi
    done <<< "$KEYWORDS"

    # 如果没有匹配到关键字,检查意图模式(正则)
    if [ "$MATCHED" = false ]; then
        PATTERNS=$(jq -r ".skills[\"$SKILL_NAME\"].promptTriggers.intentPatterns[]? // empty" "$RULES_FILE" 2>/dev/null || true)
        while IFS= read -r PATTERN; do
            [ -z "$PATTERN" ] && continue
            # 使用 grep -E 进行正则匹配(不区分大小写)
            if echo "$PROMPT" | grep -qiE "$PATTERN"; then
                MATCHED=true
                break
            fi
        done <<< "$PATTERNS"
    fi

    # 如果匹配,添加到结果
    if [ "$MATCHED" = true ]; then
        PRIORITY=$(jq -r ".skills[\"$SKILL_NAME\"].priority // \"medium\"" "$RULES_FILE")
        MATCHED_SKILLS+=("$SKILL_NAME")
        MATCHED_PRIORITIES+=("$PRIORITY")
    fi
done <<< "$SKILL_NAMES"

# 如果没有匹配的技能,静默退出
if [ ${#MATCHED_SKILLS[@]} -eq 0 ]; then
    exit 0
fi

# 输出格式化提示
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 技能激活检查"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 按优先级分组输出(兼容 bash 3.x)
CRITICAL_SKILLS=""
HIGH_SKILLS=""
MEDIUM_SKILLS=""
LOW_SKILLS=""

for i in "${!MATCHED_SKILLS[@]}"; do
    SKILL="${MATCHED_SKILLS[$i]}"
    PRIORITY="${MATCHED_PRIORITIES[$i]}"

    case "$PRIORITY" in
        critical)
            CRITICAL_SKILLS="$CRITICAL_SKILLS$SKILL|"
            ;;
        high)
            HIGH_SKILLS="$HIGH_SKILLS$SKILL|"
            ;;
        medium)
            MEDIUM_SKILLS="$MEDIUM_SKILLS$SKILL|"
            ;;
        low)
            LOW_SKILLS="$LOW_SKILLS$SKILL|"
            ;;
    esac
done

# 输出各优先级技能
if [ -n "$CRITICAL_SKILLS" ]; then
    echo "⚠️ 关键技能(必需):"
    IFS='|' read -ra SKILLS <<< "$CRITICAL_SKILLS"
    for skill in "${SKILLS[@]}"; do
        [ -n "$skill" ] && echo "  → $skill"
    done
    echo ""
fi

if [ -n "$HIGH_SKILLS" ]; then
    echo "📚 推荐技能:"
    IFS='|' read -ra SKILLS <<< "$HIGH_SKILLS"
    for skill in "${SKILLS[@]}"; do
        [ -n "$skill" ] && echo "  → $skill"
    done
    echo ""
fi

if [ -n "$MEDIUM_SKILLS" ]; then
    echo "💡 建议技能:"
    IFS='|' read -ra SKILLS <<< "$MEDIUM_SKILLS"
    for skill in "${SKILLS[@]}"; do
        [ -n "$skill" ] && echo "  → $skill"
    done
    echo ""
fi

if [ -n "$LOW_SKILLS" ]; then
    echo "📌 可选技能:"
    IFS='|' read -ra SKILLS <<< "$LOW_SKILLS"
    for skill in "${SKILLS[@]}"; do
        [ -n "$skill" ] && echo "  → $skill"
    done
    echo ""
fi

echo "操作:在响应前使用 Skill 工具"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 非阻塞 hook:总是成功退出
exit 0
