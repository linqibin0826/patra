#!/usr/bin/env bash
# ============================================================================
# deploy.sh 单测 —— stub docker/curl，不碰真实 Docker。
#   场景1 全健康部署：exit 0，last-good 记录新 tag
#   场景2 部署后不健康：自动回滚到 last-good 并复检通过，但整体 exit 1
#   场景3 镜像架构为 amd64：拦截，不执行 compose up，exit 1
#   场景4 未知服务名：入参校验拒绝，exit 2（防 dispatch 手滑打错服务名后"假成功"）
#   场景5 非法 SERVICES_JSON：入参校验拒绝，exit 2
#   场景6 按 services.json 的条目顺序部署：请求的服务一个不漏，identity 先于 gateway
# 运行：bash patra-infra/cd/deploy.test.sh
# ============================================================================
# shellcheck disable=SC2016  # 断言用单引号是有意的：延迟到 check() 内 eval 时才展开
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PASS=0; FAIL=0

setup() { # 每场景独立沙箱；导出全部 stub 控制变量与 deploy.sh 注入点
  TMP="$(mktemp -d)"
  export STUB_LOG="$TMP/calls.log" STUB_IMAGES="$TMP/images" \
         STUB_ARCH="$TMP/arch" STUB_HEALTHY="$TMP/healthy" STUB_STATE="$TMP/state"
  export SERVICES_FILE="$SCRIPT_DIR/services.json"
  export LAST_GOOD_DIR="$TMP/last-good" COMPOSE_DIR="$TMP"
  export HEALTH_RETRIES=2 HEALTH_INTERVAL=0 PULL_BACKOFFS="0"
  mkdir -p "$STUB_STATE" "$LAST_GOOD_DIR" "$TMP/bin"
  : > "$STUB_LOG"; : > "$STUB_IMAGES"; : > "$STUB_HEALTHY"
  echo arm64 > "$STUB_ARCH"

  cat > "$TMP/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $*" >> "$STUB_LOG"
case "$1" in
  image)
    shift
    case "$1" in
      inspect)
        shift
        FMT=""; if [ "$1" = "--format" ]; then FMT="$2"; shift 2; fi
        grep -qxF "$1" "$STUB_IMAGES" || exit 1
        [ "$FMT" = "{{.Architecture}}" ] && cat "$STUB_ARCH"
        exit 0 ;;
      prune) exit 0 ;;
    esac ;;
  pull) echo "$2" >> "$STUB_IMAGES" ;;
  compose)
    # compose ... up -d <svc>：从环境变量取 tag，记录为该服务当前运行 tag
    svc="${!#}"
    var="$(echo "$svc" | tr 'a-z-' 'A-Z_')_IMAGE_TAG"
    echo "${!var:-latest}" > "$STUB_STATE/current-$svc" ;;
  inspect)
    # inspect --format {{.Config.Image}} patra-<svc>
    svc="${!#}"; svc="${svc#patra-}"
    img="$(jq -r --arg s "$svc" '.services[]|select(.name==$s)|.image' "$SERVICES_FILE")"
    echo "$img:$(cat "$STUB_STATE/current-$svc")" ;;
  logs) exit 0 ;;
esac
exit 0
EOF
  cat > "$TMP/bin/curl" <<'EOF'
#!/usr/bin/env bash
url="${!#}"
port="${url#http://127.0.0.1:}"; port="${port%%/*}"
svc="$(jq -r --arg p "$port" '.services[]|select((.port|tostring)==$p)|.name' "$SERVICES_FILE")"
cur="$(cat "$STUB_STATE/current-$svc" 2>/dev/null || echo none)"
grep -qxF "$cur" "$STUB_HEALTHY" || exit 1
echo '{"status":"UP"}'
EOF
  chmod +x "$TMP/bin/docker" "$TMP/bin/curl"
  export PATH="$TMP/bin:$PATH"
}

check() { # $1=场景名 $2=期望退出码 $3=实际退出码 $4...=断言命令
  local name="$1" want="$2" got="$3"; shift 3
  local ok=1
  [ "$got" = "$want" ] || { echo "  ✗ 退出码 got=$got want=$want"; ok=0; }
  local a; for a in "$@"; do
    eval "$a" || { echo "  ✗ 断言失败: $a"; ok=0; }
  done
  if [ "$ok" = 1 ]; then echo "✓ $name"; PASS=$((PASS+1)); else echo "✗ $name"; FAIL=$((FAIL+1)); fi
}

# ---- 场景1：全健康部署 ----
setup
echo "ghcr.io/linqibin0826/patra-catalog:newsha" >> "$STUB_IMAGES"
echo newsha >> "$STUB_HEALTHY"
bash "$SCRIPT_DIR/deploy.sh" newsha '["catalog"]' > "$TMP/out" 2>&1; rc=$?
check "场景1 全健康部署" 0 "$rc" \
  '[ "$(cat "$LAST_GOOD_DIR/last-good-catalog")" = newsha ]' \
  'grep -q "up -d catalog" "$STUB_LOG"'

# ---- 场景2：不健康 → 自动回滚 ----
setup
echo oldsha > "$LAST_GOOD_DIR/last-good-catalog"
echo "ghcr.io/linqibin0826/patra-catalog:newsha" >> "$STUB_IMAGES"
echo "ghcr.io/linqibin0826/patra-catalog:oldsha" >> "$STUB_IMAGES"
echo oldsha >> "$STUB_HEALTHY"   # 只有旧 tag 健康
bash "$SCRIPT_DIR/deploy.sh" newsha '["catalog"]' > "$TMP/out" 2>&1; rc=$?
check "场景2 不健康自动回滚" 1 "$rc" \
  '[ "$(cat "$STUB_STATE/current-catalog")" = oldsha ]' \
  'grep -q "自动回滚" "$TMP/out"' \
  '[ "$(cat "$LAST_GOOD_DIR/last-good-catalog")" = oldsha ]'

# ---- 场景3：amd64 镜像拦截 ----
setup
echo "ghcr.io/linqibin0826/patra-catalog:newsha" >> "$STUB_IMAGES"
echo newsha >> "$STUB_HEALTHY"
echo amd64 > "$STUB_ARCH"
bash "$SCRIPT_DIR/deploy.sh" newsha '["catalog"]' > "$TMP/out" 2>&1; rc=$?
check "场景3 amd64 拦截" 1 "$rc" \
  '! grep -q "up -d catalog" "$STUB_LOG"'

# ---- 场景4：未知服务名拒绝 ----
setup
bash "$SCRIPT_DIR/deploy.sh" newsha '["catalog","typo"]' > "$TMP/out" 2>&1; rc=$?
check "场景4 未知服务名拒绝" 2 "$rc" \
  '! grep -q "up -d" "$STUB_LOG"' \
  'grep -q "未知服务" "$TMP/out"'

# ---- 场景5：非法 SERVICES_JSON 拒绝 ----
setup
bash "$SCRIPT_DIR/deploy.sh" newsha 'not-json' > "$TMP/out" 2>&1; rc=$?
check "场景5 非法 JSON 拒绝" 2 "$rc" \
  '! grep -q "up -d" "$STUB_LOG"'

# ---- 场景6：按 services.json 的条目顺序部署，请求的服务一个不漏 ----
setup
for s in gateway identity object-storage; do
  echo "ghcr.io/linqibin0826/patra-$s:newsha" >> "$STUB_IMAGES"
done
echo newsha >> "$STUB_HEALTHY"
bash "$SCRIPT_DIR/deploy.sh" newsha '["gateway","identity","object-storage"]' > "$TMP/out" 2>&1; rc=$?
check "场景6 按 services.json 顺序部署" 0 "$rc" \
  '[ "$(sed -n "s/.* up -d //p" "$STUB_LOG" | xargs)" = "object-storage identity gateway" ]' \
  '[ "$(cat "$LAST_GOOD_DIR/last-good-object-storage")" = newsha ]' \
  '[ "$(cat "$LAST_GOOD_DIR/last-good-identity")" = newsha ]' \
  '[ "$(cat "$LAST_GOOD_DIR/last-good-gateway")" = newsha ]'

echo "----"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = 0 ]
