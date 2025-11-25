#!/usr/bin/env bash
# 检查 patra-spring-boot-starter-test 依赖配置
#
# 该脚本执行两项检查：
# 1. scope 检查：确保已声明的依赖使用 <scope>test</scope>
# 2. 必须依赖检查：确保需要测试基础设施的模块都声明了该依赖
#
# 必须依赖 starter-test 的模块：
#   - patra-spring-boot-starter-* (除 test 自身)
#   - patra-spring-cloud-starter-*
#   - patra-{service}-adapter/app/infra/boot
#
# 不需要依赖的模块（纯 Java）：
#   - patra-{service}-domain
#   - patra-{service}-api
#   - patra-common-*
#   - patra-expr-kernel
#   - 聚合 POM（patra-{service}、patra-parent 等）
#
# 用法：
#   ./check-test-starter-scope.sh
#
# 退出码：
#   0 - 所有检查通过
#   1 - 发现配置错误

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

REPO_ROOT=$(get_repo_root)
ARTIFACT_ID="patra-spring-boot-starter-test"
SCOPE_ERRORS=()
MISSING_ERRORS=()

# 判断模块是否必须依赖 starter-test
# 返回 0 表示必须依赖，返回 1 表示不需要
requires_test_starter() {
    local module_name="$1"

    # 排除自身
    if [[ "$module_name" == "patra-spring-boot-starter-test" ]]; then
        return 1
    fi

    # 排除纯 Java 模块（在其他规则之前判断）
    if [[ "$module_name" =~ ^patra-common- ]]; then
        return 1
    fi
    if [[ "$module_name" == "patra-expr-kernel" ]]; then
        return 1
    fi
    # 排除 domain 层（纯 Java，不依赖 Spring）
    if [[ "$module_name" =~ -domain$ ]]; then
        return 1
    fi
    # 排除 api 层（仅定义接口和 DTO，纯 Java）
    if [[ "$module_name" =~ -api$ ]]; then
        return 1
    fi

    # 所有 starter 模块必须依赖
    if [[ "$module_name" =~ ^patra-spring-boot-starter- ]]; then
        return 0
    fi
    if [[ "$module_name" =~ ^patra-spring-cloud-starter- ]]; then
        return 0
    fi

    # 服务模块：adapter/app/infra/boot 必须依赖
    # 使用 [a-z-]+ 匹配多词服务名如 object-storage
    if [[ "$module_name" =~ ^patra-[a-z-]+-adapter$ ]]; then
        return 0
    fi
    if [[ "$module_name" =~ ^patra-[a-z-]+-app$ ]]; then
        return 0
    fi
    if [[ "$module_name" =~ ^patra-[a-z-]+-infra$ ]]; then
        return 0
    fi
    if [[ "$module_name" =~ ^patra-[a-z-]+-boot$ ]]; then
        return 0
    fi

    # 其他模块不需要（domain、common、expr-kernel、聚合 POM 等）
    return 1
}

# 检查 pom.xml 是否声明了 starter-test 依赖
has_test_starter_dependency() {
    local pom_file="$1"
    grep -q "<artifactId>$ARTIFACT_ID</artifactId>" "$pom_file"
}

# 检查依赖是否有 test scope
has_test_scope() {
    local pom_file="$1"

    # 提取依赖块并检查 scope
    local in_dep=false
    local has_artifact=false
    local has_scope=false

    while IFS= read -r line; do
        if [[ "$line" == *"<dependency>"* ]]; then
            in_dep=true
            has_artifact=false
            has_scope=false
        fi

        if [[ "$in_dep" == true ]]; then
            if [[ "$line" == *"<artifactId>$ARTIFACT_ID</artifactId>"* ]]; then
                has_artifact=true
            fi
            if [[ "$line" == *"<scope>test</scope>"* ]]; then
                has_scope=true
            fi
        fi

        if [[ "$line" == *"</dependency>"* && "$in_dep" == true ]]; then
            if [[ "$has_artifact" == true ]]; then
                if [[ "$has_scope" == true ]]; then
                    return 0
                else
                    return 1
                fi
            fi
            in_dep=false
        fi
    done < "$pom_file"

    return 1
}

# 从 pom.xml 提取项目自身的 artifactId（跳过 parent 块）
get_module_name() {
    local pom_file="$1"
    # 跳过 <parent> 块，取项目自身的 artifactId
    awk '
        /<parent>/,/<\/parent>/ { next }
        /<artifactId>/ {
            gsub(/.*<artifactId>/, "")
            gsub(/<\/artifactId>.*/, "")
            gsub(/[[:space:]]/, "")
            print
            exit
        }
    ' "$pom_file"
}

# 检查是否是聚合 POM（包含 <modules> 元素）
is_aggregator_pom() {
    local pom_file="$1"
    grep -q "<modules>" "$pom_file"
}

echo "检查 $ARTIFACT_ID 依赖配置..."
echo

# 查找所有 pom.xml 文件
pom_files=$(find "$REPO_ROOT" -name "pom.xml" -type f | sort)

checked=0
required_count=0
optional_count=0

for pom in $pom_files; do
    relative_path="${pom#$REPO_ROOT/}"
    module_name=$(get_module_name "$pom")

    # 跳过聚合 POM
    if is_aggregator_pom "$pom"; then
        ((checked++))
        continue
    fi

    # 跳过 starter-test 自身
    if [[ "$module_name" == "$ARTIFACT_ID" ]]; then
        ((checked++))
        continue
    fi

    # 检查 1：如果声明了依赖，必须有 test scope
    if has_test_starter_dependency "$pom"; then
        if ! has_test_scope "$pom"; then
            SCOPE_ERRORS+=("$relative_path ($module_name): 依赖缺少 <scope>test</scope>")
        fi
    fi

    # 检查 2：必须依赖的模块是否声明了依赖
    if requires_test_starter "$module_name"; then
        ((required_count++))
        if ! has_test_starter_dependency "$pom"; then
            MISSING_ERRORS+=("$relative_path ($module_name): 缺少 $ARTIFACT_ID 依赖")
        fi
    else
        ((optional_count++))
    fi

    ((checked++))
done

echo "已检查 $checked 个模块"
echo "  - 必须依赖: $required_count 个"
echo "  - 不需要依赖: $optional_count 个"
echo

# 输出结果
has_error=false

if [[ ${#SCOPE_ERRORS[@]} -gt 0 ]]; then
    has_error=true
    echo "❌ Scope 错误 (${#SCOPE_ERRORS[@]} 处)："
    for error in "${SCOPE_ERRORS[@]}"; do
        echo "  - $error"
    done
    echo
fi

if [[ ${#MISSING_ERRORS[@]} -gt 0 ]]; then
    has_error=true
    echo "❌ 缺少依赖 (${#MISSING_ERRORS[@]} 处)："
    for error in "${MISSING_ERRORS[@]}"; do
        echo "  - $error"
    done
    echo
fi

if [[ "$has_error" == true ]]; then
    echo "修复方法：添加以下依赖到对应模块的 pom.xml"
    echo
    echo "  <dependency>"
    echo "      <groupId>com.patra</groupId>"
    echo "      <artifactId>$ARTIFACT_ID</artifactId>"
    echo "      <scope>test</scope>"
    echo "  </dependency>"
    exit 1
else
    echo "✅ 所有检查通过"
    exit 0
fi
