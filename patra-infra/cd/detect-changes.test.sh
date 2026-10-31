#!/usr/bin/env bash
# detect-changes.sh classify 的零依赖测试。用法：bash patra-infra/cd/detect-changes.test.sh
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DETECT="$SCRIPT_DIR/detect-changes.sh"
PASS=0; FAIL=0

# 断言：把 $1（换行分隔的变更文件）喂给 classify，比较 jq 取出的 $2 字段 == 期望 $3
assert_field() {
  local files="$1" field="$2" expected="$3" desc="$4"
  local got
  got="$(printf '%s\n' "$files" | bash "$DETECT" classify | jq -c "$field")"
  if [ "$got" = "$expected" ]; then PASS=$((PASS+1)); printf '  ✓ %s\n' "$desc"
  else FAIL=$((FAIL+1)); printf '  ✗ %s\n    field %s 期望 %s 实得 %s\n' "$desc" "$field" "$expected" "$got"; fi
}

echo "== 场景 1：仅前端 =="
assert_field "patra-portal/app/page.tsx" '.backend_units' '[]' "前端 backend_units 空"
assert_field "patra-portal/app/page.tsx" '.portal_changed' 'true' "前端 portal_changed"
assert_field "patra-portal/app/page.tsx" '.docs_only' 'false' "前端非 docs_only"

echo "== 场景 2：仅 docs =="
assert_field "$(printf 'README.md\ndocs/x.html')" '.docs_only' 'true' "docs_only"
assert_field "README.md" '.backend_units' '[]' "docs backend 空"

echo "== 场景 3：catalog 私有实现（不外溢）=="
F="patra-api/patra-catalog/patra-catalog-infra/src/main/java/X.java"
assert_field "$F" '.backend_units' '["catalog"]' "catalog 私有只跑 catalog"
assert_field "$F" '.full_run' 'false' "catalog 私有非全量"
assert_field "$F" '.coverage_mode' '"flags"' "catalog coverage=flags"

echo "== 场景 4：registry-api 契约扇出 =="
F="patra-api/patra-registry/patra-registry-api/src/main/java/X.java"
assert_field "$F" '.backend_units' '["catalog","registry"]' "registry-api 扇出 consumers"
assert_field "$F" '.full_run' 'false' "registry-api 非全量"

echo "== 场景 5：catalog-api 契约扇出 =="
F="patra-api/patra-catalog/patra-catalog-api/src/main/java/X.java"
assert_field "$F" '.backend_units' '["catalog","identity","interaction"]' "catalog-api 扇出 consumers"

echo "== 场景 6：全局 path → 全量；foundation 模块 → 扇出（非全量）=="
assert_field "build-logic/src/main/kotlin/x.gradle.kts" '.full_run' 'true' "build-logic 全量"
assert_field "patra-tool/patra-tool-core/src/main/java/X.java" '.full_run' 'false' "patra-tool-core 非全量"
assert_field "patra-tool/patra-tool-core/src/main/java/X.java" '.backend_units' '["catalog","foundation","gateway","identity","interaction","notification","registry"]' "patra-tool-core 扇出全部 6 个服务与 foundation"

echo "== 场景 7：workflow 自身变更 → 全量后端 + portal =="
assert_field ".github/workflows/ci.yml" '.full_run' 'true' "workflow 全量"
assert_field ".github/workflows/ci.yml" '.portal_changed' 'true' "workflow 额外含 portal"

echo "== 场景 8：混合（catalog + portal）=="
F="$(printf 'patra-api/patra-catalog/patra-catalog-app/src/main/java/X.java\npatra-portal/app/x.tsx')"
assert_field "$F" '.backend_units' '["catalog"]' "混合 backend=catalog"
assert_field "$F" '.portal_changed' 'true' "混合 portal=true"

echo "== 场景 9：仅 learn（与 portal 同构）=="
assert_field "patra-learn/src/app/page.tsx" '.learn_changed' 'true' "learn learn_changed"
assert_field "patra-learn/src/app/page.tsx" '.backend_units' '[]' "learn backend_units 空"
assert_field "patra-learn/src/app/page.tsx" '.portal_changed' 'false' "learn 不触发 portal"
assert_field "docs/foo.md" '.learn_changed' 'false' "docs-only 不触发 learn"
assert_field ".github/workflows/ci.yml" '.learn_changed' 'true' "workflow 额外含 learn"

echo "== 场景 10：纯基建路径（应用以外的 compose 栈 / 运维脚本）→ 不触发后端 =="
assert_field "patra-infra/scripts/compose-all.sh" '.backend_units' '[]' "运维脚本 backend 空"
assert_field "patra-infra/scripts/compose-all.sh" '.full_run' 'false' "运维脚本非全量"
assert_field "patra-infra/docker/docker-compose.tailnet.yaml" '.backend_units' '[]' "tailnet 栈 backend 空"
assert_field "patra-infra/docker/docker-compose.core.yaml" '.backend_units' '[]' "core 栈 backend 空"
assert_field "patra-infra/docker/prometheus/prometheus.yml" '.backend_units' '[]' "基建栈配置目录 backend 空"
assert_field "patra-infra/docker/rabbitmq/conf.d/20-patra.conf" '.full_run' 'false' "rabbitmq 配置目录非全量"
assert_field "patra-infra/docker/rabbitmq/conf.d/20-patra.conf" '.backend_units' '[]' "rabbitmq 配置目录 backend 空"
assert_field "patra-infra/docker/.env" '.backend_units' '[]' "基建栈 .env backend 空"
assert_field "patra-infra/docker/docker-compose.tailnet.yaml" '.docs_only' 'false' "纯基建不算 docs_only"
F="$(printf 'patra-infra/docker/docker-compose.tailnet.yaml\npatra-api/patra-catalog/patra-catalog-infra/src/main/java/X.java')"
assert_field "$F" '.backend_units' '["catalog"]' "基建 + catalog 混合只跑 catalog"

echo "== 场景 11：应用编排与应用环境变量仍保守全量 =="
assert_field "patra-infra/docker/docker-compose.apps.yaml" '.full_run' 'true' "apps 栈全量"
assert_field "patra-infra/docker/service.Dockerfile" '.full_run' 'true' "共享 Dockerfile 全量"
assert_field "patra-infra/docker/.env.common" '.full_run' 'true' "应用 .env.common 全量"

echo "== 场景 12：前端 / 学习站目录下的 Markdown 是文档，不触发 portal / learn（与 portal-cd / learn-cd 的 '!**/*.md' 口径一致）=="
F="patra-portal/docs/design-briefs/v0.7/papers-list.md"
assert_field "$F" '.portal_changed' 'false' "portal 设计简报不触发 portal"
assert_field "$F" '.docs_only' 'true' "portal 设计简报算 docs_only"
assert_field "patra-portal/.claude/rules/tokens.md" '.portal_changed' 'false' "portal 规则文档不触发 portal"
assert_field "patra-learn/README.md" '.learn_changed' 'false' "learn README 不触发 learn"
assert_field "patra-learn/README.md" '.docs_only' 'true' "learn README 算 docs_only"
F="$(printf 'patra-portal/docs/x.md\npatra-portal/src/app/page.tsx')"
assert_field "$F" '.portal_changed' 'true' "文档 + 前端代码混合仍触发 portal"
assert_field "$F" '.docs_only' 'false' "文档 + 前端代码混合非 docs_only"

echo "== 场景 13：identity 单元（私有改动只跑 identity；全量运行也包含 identity）=="
F="patra-api/patra-identity/patra-identity-app/src/main/java/X.java"
assert_field "$F" '.backend_units' '["identity"]' "identity 私有只跑 identity"
assert_field "settings.gradle.kts" '.backend_units | index("identity") != null' 'true' "全量运行包含 identity"

echo "== 场景 14：Nacos 配置 → foundation（CI 检查）+ 用到它的服务（CD 推送后重部署）；推送脚本 → foundation =="
F="patra-infra/nacos/dev/patra-catalog.yaml"
assert_field "$F" '.nacos_changed' 'true' "nacos yaml 触发推送"
assert_field "$F" '.backend_units' '["catalog","foundation"]' "服务的 nacos yaml 重部署该服务（启动时读的配置才生效）"
assert_field "patra-infra/nacos/dev/patra-common.yaml" '.backend_units' '["catalog","foundation","gateway","identity","interaction","notification","registry"]' "公共 nacos yaml 重部署全部 6 个服务"
assert_field "patra-infra/nacos/dev/patra-common.yaml" '.full_run' 'false' "公共 nacos yaml 非全量"
assert_field "patra-infra/nacos/dev/patra-unknown.yaml" '.backend_units' '["foundation"]' "对不上服务的 nacos yaml 只跑 foundation"
assert_field "$F" '.full_run' 'false' "nacos yaml 非全量"
assert_field "patra-infra/nacos/dev/README.md" '.nacos_changed' 'false' "nacos README 不触发推送"
assert_field "patra-infra/nacos/dev/README.md" '.docs_only' 'true' "nacos README 算 docs_only"
assert_field "patra-infra/scripts/nacos-push.sh" '.nacos_changed' 'true' "推送脚本触发推送"
assert_field "patra-infra/scripts/nacos-push.sh" '.backend_units' '["foundation"]' "推送脚本跑 foundation（IT）"
assert_field "patra-infra/scripts/nacos-lib.sh" '.nacos_changed' 'true' "推送脚本依赖的函数库触发推送"
assert_field "patra-infra/scripts/nacos-init.sh" '.nacos_changed' 'false' "nacos-init 不触发推送"
assert_field "patra-infra/scripts/nacos-init.sh" '.backend_units' '[]' "nacos-init 仍是纯基建"
F="$(printf 'patra-infra/nacos/dev/patra-identity.yaml\npatra-api/patra-catalog/patra-catalog-infra/src/main/java/X.java')"
assert_field "$F" '.nacos_changed' 'true' "yaml + catalog 混合仍推送"
assert_field "$F" '.backend_units' '["catalog","foundation","identity"]' "yaml + catalog 混合：两个服务和 foundation 都跑"
assert_field "settings.gradle.kts" '.nacos_changed' 'true' "全量运行也推送"
assert_field "patra-api/patra-catalog/patra-catalog-infra/src/main/java/X.java" '.nacos_changed' 'false' "纯服务代码不推送"
got="$(bash "$DETECT" dispatch catalog | jq -c '.nacos_changed')"
if [ "$got" = "false" ]; then PASS=$((PASS+1)); echo "  ✓ dispatch 单服务（回滚）不推送"; else FAIL=$((FAIL+1)); echo "  ✗ dispatch 单服务 nacos_changed 实得 $got"; fi
got="$(bash "$DETECT" dispatch all | jq -c '.nacos_changed')"
if [ "$got" = "true" ]; then PASS=$((PASS+1)); echo "  ✓ dispatch all 推送"; else FAIL=$((FAIL+1)); echo "  ✗ dispatch all nacos_changed 实得 $got"; fi

echo "== 场景 15：interaction 单元（私有改动只跑 interaction；全量运行包含 interaction；它的 nacos yaml 只重部署它）=="
F="patra-api/patra-interaction/patra-interaction-infra/src/main/resources/db/migration/subject/V1__x.sql"
assert_field "$F" '.backend_units' '["interaction"]' "interaction 私有只跑 interaction"
assert_field "settings.gradle.kts" '.backend_units | index("interaction") != null' 'true' "全量运行包含 interaction"
assert_field "patra-infra/nacos/dev/patra-interaction.yaml" '.backend_units' '["foundation","interaction"]' "interaction 的 nacos yaml 重部署 interaction"
echo "== 场景 16：notification 单元（私有改动只跑 notification；全量运行包含 notification；它的 nacos yaml 只重部署它）=="
F="patra-api/patra-notification/patra-notification-infra/src/main/java/X.java"
assert_field "$F" '.backend_units' '["notification"]' "notification 私有只跑 notification"
assert_field "settings.gradle.kts" '.backend_units | index("notification") != null' 'true' "全量运行包含 notification"
assert_field "patra-infra/nacos/dev/patra-notification.yaml" '.backend_units' '["foundation","notification"]' "notification 的 nacos yaml 重部署 notification"
echo
echo "通过 $PASS / 失败 $FAIL"
[ "$FAIL" -eq 0 ]
