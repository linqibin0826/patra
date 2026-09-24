#!/usr/bin/env bash
# ============================================================================
# 受影响单元路由 SSOT —— ci.yml 与 cd.yml 共用。输出一个 JSON 对象到 stdout：
#   { "backend_units":[...], "portal_changed":bool, "learn_changed":bool,
#     "docs_only":bool, "full_run":bool, "coverage_mode":"none|flags|full" }
#   backend_units ⊆ [registry,object-storage,catalog,ingest,gateway,foundation]
#
# 模式：
#   detect-changes.sh pr <BASE_SHA> <HEAD_SHA>    # pull_request：merge-base..head
#   detect-changes.sh push <BEFORE_SHA> <AFTER_SHA># push:main
#   detect-changes.sh schedule                     # nightly：全量
#   detect-changes.sh dispatch <SERVICE|all|"">    # 手动（CD 回滚）
#   detect-changes.sh classify                     # 从 stdin 读变更文件清单（测试用）
#
# CD 消费：services = [backend_units 去掉 "foundation"]（foundation 不可部署）。
# 兼容 bash 3.2。依赖：git、jq、同目录 module-graph.json。
# ============================================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRAPH="$SCRIPT_DIR/module-graph.json"
ALL_UNITS='["registry","object-storage","catalog","ingest","gateway","foundation"]'
LEARN_CHANGED=false   # 全局初始化：防环境注入假阳性 / 非布尔值炸 --argjson

emit() { # $1=units_json $2=portal $3=docs_only $4=full_run $5=coverage_mode（learn 走全局 LEARN_CHANGED）
  local learn="${LEARN_CHANGED:-false}"
  jq -cn --argjson u "$1" --argjson p "$2" --argjson le "$learn" --argjson d "$3" --argjson f "$4" --arg c "$5" \
    '{backend_units:$u, portal_changed:$p, learn_changed:$le, docs_only:$d, full_run:$f, coverage_mode:$c}'
}
emit_full() { emit "$ALL_UNITS" "${1:-false}" false true full; }

# 把 stdin 的变更文件清单分类，输出 JSON 对象。
classify() {
  local full=false portal=false docs_all=true
  local units_file; units_file="$(mktemp)"; trap 'rm -f "$units_file"' RETURN
  local saw_any=false f hit
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    saw_any=true
    # workflow 自身变更：全量后端 + 额外触发 portal/learn（验证整条管线）
    case "$f" in .github/workflows/*) full=true; portal=true; LEARN_CHANGED=true; docs_all=false; continue;; esac
    # 全局影响、模块图覆盖不到的非 project 路径 → 全量
    case "$f" in
      build-logic/*|gradle/*|gradlew|gradlew.bat|build.gradle.kts|settings.gradle.kts|gradle.properties) full=true; docs_all=false; continue;;
      patra-infra/docker/service.Dockerfile|patra-infra/docker/docker-compose.apps.yaml) full=true; docs_all=false; continue;;
      patra-infra/cd/*) full=true; docs_all=false; continue;;
    esac
    # 纯基建路径：应用以外的 compose 栈及其配置、基建栈 .env、运维脚本、本地 OTel agent 配置。
    # 不进应用镜像也不影响应用容器，由 mini 上 compose-all.sh 手动生效 → 不触发任何后端单元。
    # 注意 apps 栈与 service.Dockerfile 已在上面判全量；应用容器的 .env.common / .env.<svc> 未列入，
    # 仍走下方「模块图外 → 全量」保守分支（改了需重部署才生效）。
    case "$f" in
      patra-infra/scripts/*|patra-infra/docker/docker-compose.*.yaml) docs_all=false; continue;;
      patra-infra/docker/.env|patra-infra/docker/.env.dev|patra-infra/docker/*.example) docs_all=false; continue;;
      patra-infra/docker/alertmanager/*|patra-infra/docker/grafana/*|patra-infra/docker/loki/*) docs_all=false; continue;;
      patra-infra/docker/mysql-ops/*|patra-infra/docker/otel-agent/*|patra-infra/docker/otel-collector/*) docs_all=false; continue;;
      patra-infra/docker/postgres/*|patra-infra/docker/prometheus/*|patra-infra/docker/tempo/*) docs_all=false; continue;;
    esac
    case "$f" in patra-portal/*) portal=true; docs_all=false; continue;; esac
    case "$f" in patra-learn/*) LEARN_CHANGED=true; docs_all=false; continue;; esac
    case "$f" in *.md|docs/*|.gitignore|.editorconfig|LICENSE|.claude/*) continue;; esac
    docs_all=false
    # 文件落到哪个模块（module-graph dir 为其最长前缀）→ 取该模块 impacts
    hit="$(jq -r --arg f "$f" '
      [.modules[] | .dir as $d | select(($f | startswith($d + "/")) or ($f == $d))]
      | sort_by(.dir | length) | last | (.impacts // []) | .[]' "$GRAPH")"
    if [ -z "$hit" ]; then full=true; else printf '%s\n' "$hit" >> "$units_file"; fi
  done

  if [ "$saw_any" = false ]; then emit '[]' false false false none; return; fi
  if [ "$full" = true ]; then emit "$ALL_UNITS" "$portal" false true full; return; fi
  local units_json; units_json="$(sort -u "$units_file" | jq -cRs 'split("\n") | map(select(length > 0))')"
  if [ "$units_json" = '[]' ]; then
    if [ "$portal" = true ]; then emit '[]' true false false none
    elif [ "$docs_all" = true ]; then emit '[]' false true false none
    else emit '[]' false false false none; fi
  else
    emit "$units_json" "$portal" false false flags
  fi
}

MODE="${1:-}"
case "$MODE" in
  schedule) LEARN_CHANGED=true; emit_full true ;;   # nightly：全量 + 触发 portal/learn（UI 回归）
  classify) classify ;;
  dispatch)
    SVC="${2:-}"
    if [ -z "$SVC" ] || [ "$SVC" = "all" ]; then emit_full
    else emit "$(jq -cn --arg s "$SVC" '[$s]')" false false false flags; fi
    ;;
  pr)
    BASE="${2:?pr 模式需 BASE_SHA}"; HEAD="${3:?pr 模式需 HEAD_SHA}"
    git diff --name-only "$(git merge-base "$BASE" "$HEAD")" "$HEAD" | classify
    ;;
  push)
    BEFORE="${2:-}"; AFTER="${3:-}"; ZERO="0000000000000000000000000000000000000000"
    if [ -z "$BEFORE" ] || [ "$BEFORE" = "$ZERO" ]; then emit_full
    else git diff --name-only "$BEFORE" "$AFTER" | classify; fi
    ;;
  *)
    echo "用法: detect-changes.sh pr <base> <head> | push <before> <after> | schedule | dispatch <svc|all> | classify" >&2
    exit 1
    ;;
esac
