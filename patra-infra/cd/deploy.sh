#!/usr/bin/env bash
# ============================================================================
# Mac mini 部署执行器 —— cd.yml / portal-cd.yml 的 deploy step 共用。
#   deploy.sh <TAG> <SERVICES_JSON>
#     TAG            镜像 tag（commit sha 或回滚目标）
#     SERVICES_JSON  要部署的服务名 JSON 数组，如 '["catalog","ingest"]'
#
# 职责（按序）：
#   1. 镜像就位：本地缺失才从 GHCR 拉（指数退避 30/60/120s）。正常部署时镜像
#      已由本机原生构建、天然命中；仅回滚且本地缓存被清时回源 GHCR。
#   2. 架构断言：必须 arm64（防止历史 amd64 归档被回滚拉回；2026-08 事故复盘）
#   3. 按 services.json 的条目顺序 compose up（object-storage 最先：catalog/ingest 运行时依赖它）
#   4. 健康检查：轮询 127.0.0.1:<port><healthPath>（127.0.0.1 而非 localhost——
#      后者可解析到 ::1 导致误报，portal healthcheck 实际踩坑）
#   5. 部署后验证：运行容器镜像 == 期望 tag（防 up 静默落到旧镜像）
#   6. 自动回滚：某服务不健康 → 回滚该服务到 last-good 并复检；整体仍 exit 1
#   7. 服务级 last-good 记录：每个服务验证通过即记录（不等整批）
#
# 元数据来自同目录 services.json（healthMatch 缺省=仅要求 HTTP 成功）。
# 测试注入点（deploy.test.sh 用，均带生产默认值）：
#   SERVICES_FILE / COMPOSE_DIR / LAST_GOOD_DIR / DOCKER / CURL
#   HEALTH_RETRIES / HEALTH_INTERVAL / PULL_BACKOFFS
# ============================================================================
set -euo pipefail

TAG="${1:?用法: deploy.sh <TAG> <SERVICES_JSON>}"
SERVICES_JSON="${2:?用法: deploy.sh <TAG> <SERVICES_JSON>}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICES_FILE="${SERVICES_FILE:-$SCRIPT_DIR/services.json}"
COMPOSE_DIR="${COMPOSE_DIR:-$SCRIPT_DIR/../docker}"
LAST_GOOD_DIR="${LAST_GOOD_DIR:-$HOME/.patra/cd}"
DOCKER="${DOCKER:-docker}"
CURL="${CURL:-curl}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"
PULL_BACKOFFS="${PULL_BACKOFFS:-30 60 120}"

meta() { # $1=svc $2=field → 值（字段缺省输出空）
  jq -r --arg s "$1" --arg f "$2" '.services[] | select(.name==$s) | .[$f] // empty' "$SERVICES_FILE"
}

tag_var() { echo "$(echo "$1" | tr 'a-z-' 'A-Z_')_IMAGE_TAG"; }

ensure_image() { # $1=img $2=tag → 0 就位
  $DOCKER image inspect "$1:$2" >/dev/null 2>&1 && return 0
  echo "本地无 $1:$2，从 GHCR 回源拉取"
  local d
  for d in $PULL_BACKOFFS; do
    $DOCKER pull "$1:$2" && return 0
    echo "pull 失败，${d}s 后重试..."; sleep "$d"
  done
  return 1
}

assert_arm64() { # $1=img:tag
  local arch; arch="$($DOCKER image inspect --format '{{.Architecture}}' "$1")"
  [ "$arch" = "arm64" ] || { echo "✗ $1 架构为 ${arch}（期望 arm64）"; return 1; }
}

up_service() { # $1=svc $2=tag
  local var; var="$(tag_var "$1")"
  # shellcheck disable=SC2086  # $DOCKER 需要词拆分（测试注入 stub 时为多词命令）
  (cd "$COMPOSE_DIR" && env "$var=$2" $DOCKER compose -f docker-compose.apps.yaml up -d "$1")
}

health_ok() { # $1=svc
  local port path match out
  port="$(meta "$1" port)"; path="$(meta "$1" healthPath)"; match="$(meta "$1" healthMatch)"
  out="$($CURL -fs "http://127.0.0.1:${port}${path}")" || return 1
  [ -z "$match" ] || echo "$out" | grep -q "$match"
}

wait_healthy() { # $1=svc
  local i
  for i in $(seq 1 "$HEALTH_RETRIES"); do
    health_ok "$1" && { echo "✓ $1 健康"; return 0; }
    echo "等待 $1 健康... ($i/$HEALTH_RETRIES)"; sleep "$HEALTH_INTERVAL"
  done
  return 1
}

verify_running_tag() { # $1=svc $2=img $3=tag
  local actual; actual="$($DOCKER inspect --format '{{.Config.Image}}' "patra-$1")"
  [ "$actual" = "$2:$3" ] || { echo "✗ patra-$1 运行镜像为 ${actual}（期望 $2:$3）"; return 1; }
}

rollback() { # $1=svc → 0 已回滚且健康
  local lg_file="$LAST_GOOD_DIR/last-good-$1" lg img
  [ -f "$lg_file" ] || { echo "⚠ $1 无 last-good 记录，无法自动回滚"; return 1; }
  lg="$(cat "$lg_file")"; img="$(meta "$1" image)"
  [ "$lg" != "$TAG" ] || { echo "⚠ $1 的 last-good 即当前 tag，跳过回滚"; return 1; }
  echo "⟲ $1 自动回滚到 last-good ${lg}"
  ensure_image "$img" "$lg" && up_service "$1" "$lg" && wait_healthy "$1"
}

# ---- 入参校验：拒绝非法列表与未知服务名（防 dispatch 手滑打错服务名后"假成功"）----
echo "$SERVICES_JSON" | jq -e 'type == "array" and length > 0 and all(.[]; type == "string")' >/dev/null 2>&1 \
  || { echo "✗ SERVICES_JSON 必须是非空的服务名字符串数组，实际: $SERVICES_JSON"; exit 2; }
for req in $(echo "$SERVICES_JSON" | jq -r '.[]'); do
  jq -e --arg s "$req" '.services | any(.name == $s)' "$SERVICES_FILE" >/dev/null \
    || { echo "✗ 未知服务: ${req}（不在 services.json）"; exit 2; }
done

# ---- 主流程 ----
mkdir -p "$LAST_GOOD_DIR"
# 部署顺序只取 services.json 的条目顺序：请求部署的服务只要在 services.json 里就一定会部署，
# 不会因为漏改另一份清单而被静默跳过（不在 services.json 里的服务已被上面的入参校验拒绝）
ORDER="$(jq -r '.services[].name' "$SERVICES_FILE")"
fail=0
deployed=""
for svc in $ORDER; do
  echo "$SERVICES_JSON" | jq -e --arg s "$svc" 'index($s) != null' >/dev/null || continue
  img="$(meta "$svc" image)"
  echo "===== deploy ${svc} (${img}:${TAG}) ====="
  if ! ensure_image "$img" "$TAG" || ! assert_arm64 "$img:$TAG"; then
    echo "✗ ${svc} 镜像未就位或架构错误，跳过部署（现行版本不受影响）"; fail=1; continue
  fi
  if ! up_service "$svc" "$TAG"; then
    echo "✗ ${svc} compose up 失败"; fail=1; continue
  fi
  if wait_healthy "$svc" && verify_running_tag "$svc" "$img" "$TAG"; then
    echo "$TAG" > "$LAST_GOOD_DIR/last-good-$svc"
    deployed="$deployed $svc"
  else
    echo "✗ ${svc} 部署后不健康/验证失败，最近日志："
    $DOCKER logs "patra-$svc" --tail 80 2>&1 || true
    fail=1
    if rollback "$svc"; then echo "⟲ ${svc} 已回滚并恢复健康"; else echo "‼ ${svc} 回滚失败或不可用，需人工介入"; fi
  fi
done

$DOCKER image prune -f >/dev/null 2>&1 || true
if [ "$fail" = 0 ]; then
  echo "✓ 全部部署成功:${deployed} (tag=${TAG})"
else
  echo "✗ 存在失败服务（详见上文），job 以失败退出"
  exit 1
fi
