#!/bin/bash
# Nacos 加固的验收：逐项打印 PASS / FAIL，任意一项 FAIL 返回 1；参数错误或缺凭据返回 2。
# 用法（mini 上，仓库根目录）：
#   bash patra-infra/scripts/nacos-verify.sh                     # 第 1～10 项
#   bash patra-infra/scripts/nacos-verify.sh --expect-services   # 另查第 11 项：6 个服务都已注册
# 输入同 nacos-init.sh：NACOS_ADDR、NACOS_CONSOLE_ADDR、NACOS_SECRETS_DIR；凭据可用同名环境变量覆盖。
# 只读写探针：配置 nacos-verify-probe*.yaml、服务 nacos-verify-probe，结束时清掉。不碰任何真实配置和密钥。
set -o pipefail
# shellcheck source=nacos-lib.sh source-path=SCRIPTDIR
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/nacos-lib.sh"

EXPECT_SERVICES=false
case "${1:-}" in
  --expect-services) EXPECT_SERVICES=true ;;
  "") ;;
  *) echo "用法: $0 [--expect-services]" >&2; exit 2 ;;
esac

nacos_load NACOS_ADMIN_PASSWORD nacos-admin.env || exit 2
nacos_load NACOS_USERNAME nacos.env || exit 2
nacos_load NACOS_PASSWORD nacos.env || exit 2
nacos_load NACOS_PUSH_USERNAME nacos-push.env || exit 2
nacos_load NACOS_PUSH_PASSWORD nacos-push.env || exit 2

PROBE=nacos-verify-probe.yaml
PROBE_PUSH=nacos-verify-probe-push.yaml
PROBE_LEAK=nacos-verify-probe-leak.yaml
PROBE_SVC=nacos-verify-probe
PROBE_IP=192.0.2.1 # TEST-NET-1，保证探针实例不会真的被调用
SERVICES="patra-registry patra-object-storage patra-catalog patra-ingest patra-identity patra-gateway"
PASS=0
FAIL=0
AT=""
APT=""
PT=""
TMP=""

pass() { PASS=$((PASS + 1)); printf '  PASS %2s  %s\n' "$1" "$2"; }
fail() { FAIL=$((FAIL + 1)); printf '  FAIL %2s  %s（%s）\n' "$1" "$2" "$3"; }
detail() { echo "HTTP $NACOS_HTTP code $(nacos_code)"; }

# cfg METHOD TOKEN NAMESPACE GROUP DATAID：发布（POST，正文固定为 probe: 1）、读（GET）、删（DELETE）一份配置
cfg() {
  if [ "$1" = POST ]; then
    nacos_api POST /v3/admin/cs/config "$2" "namespaceId=$3" "groupName=$4" "dataId=$5" "content=probe: 1" type=yaml
  else
    nacos_api "$1" /v3/admin/cs/config "$2" "namespaceId=$3" "groupName=$4" "dataId=$5"
  fi
}

# inst METHOD TOKEN NAMESPACE：注册（POST）或注销（DELETE）探针实例
inst() {
  nacos_api "$1" /v3/admin/ns/instance "$2" "namespaceId=$3" groupName=DEFAULT_GROUP \
    "serviceName=$PROBE_SVC" "ip=$PROBE_IP" port=9 ephemeral=false
}

# push_import NAMESPACE：patra-push 带 groupName=DEFAULT_GROUP 导入一个 zip，覆盖 DEFAULT_GROUP/$PROBE。结果在 NACOS_HTTP
push_import() {
  TMP="${TMP:-$(mktemp -d)}"
  mkdir -p "$TMP/zip/DEFAULT_GROUP"
  printf 'metadata:\n- dataId: %s\n  group: DEFAULT_GROUP\n  type: yaml\n' "$PROBE" >"$TMP/zip/.metadata.yml"
  printf 'probe: tampered\n' >"$TMP/zip/DEFAULT_GROUP/$PROBE"
  rm -f "$TMP/import.zip"
  (cd "$TMP/zip" && zip -q -r ../import.zip .metadata.yml DEFAULT_GROUP) || { NACOS_HTTP=zip; return 0; }
  NACOS_HTTP="$(curl -sS --noproxy '*' -m 15 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $PT" \
    -F "file=@$TMP/import.zip" -F "namespaceId=$1" -F groupName=DEFAULT_GROUP -F policy=OVERWRITE \
    "$NACOS_ADDR/nacos/v3/admin/cs/config/import" 2>/dev/null)" || NACOS_HTTP=000
}

# push_clone CONFIG_ID：patra-push 把一份配置从 dev-secrets 克隆到 dev 的 $PROBE_LEAK。结果在 NACOS_HTTP
push_clone() {
  NACOS_HTTP="$(curl -sS --noproxy '*' -m 15 -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $PT" \
    -H 'Content-Type: application/json' \
    -d "[{\"configId\":$1,\"targetDataId\":\"$PROBE_LEAK\",\"targetGroupName\":\"DEFAULT_GROUP\"}]" \
    "$NACOS_ADDR/nacos/v3/admin/cs/config/clone?namespaceId=dev&sourceNamespaceId=dev-secrets&groupName=DEFAULT_GROUP&policy=OVERWRITE" \
    2>/dev/null)" || NACOS_HTTP=000
}

# content NAMESPACE DATAID：用管理员令牌读 DEFAULT_GROUP 里一份配置的正文；不存在时输出「不存在」
content() {
  cfg GET "$AT" "$1" DEFAULT_GROUP "$2"
  if nacos_ok && jq -e '.data | type == "object"' <<<"$NACOS_BODY" >/dev/null 2>&1; then
    jq -r '.data.content' <<<"$NACOS_BODY"
  else
    echo 不存在
  fi
}

# shellcheck disable=SC2329  # 由下面的 trap 调用
cleanup() {
  [ -n "$TMP" ] && rm -rf "$TMP"
  [ -n "$AT" ] || return 0
  local ns
  cfg DELETE "$AT" dev DEFAULT_GROUP "$PROBE"
  cfg DELETE "$AT" dev DEFAULT_GROUP "$PROBE_PUSH"
  cfg DELETE "$AT" dev DEFAULT_GROUP "$PROBE_LEAK"
  cfg DELETE "$AT" dev-secrets DEFAULT_GROUP "$PROBE"
  cfg DELETE "$AT" dev-secrets DEFAULT_GROUP "$PROBE_PUSH"
  cfg DELETE "$AT" public DEFAULT_GROUP "$PROBE_PUSH"
  for ns in public dev; do
    inst DELETE "$AT" "$ns"
    nacos_api DELETE /v3/admin/ns/service "$AT" "namespaceId=$ns" groupName=DEFAULT_GROUP "serviceName=$PROBE_SVC"
  done
}
trap cleanup EXIT

echo "Nacos 验收：$NACOS_ADDR"

D="版本 3.2.4、PG 存储、鉴权开启"
nacos_api GET /v3/admin/core/state ""
state="$(jq -r '.data | "\(.version) \(.datasource_platform) \(.auth_enabled)"' <<<"$NACOS_BODY" 2>/dev/null)"
if [ "$state" = "3.2.4 postgresql true" ]; then pass 1 "$D"; else fail 1 "$D" "实际：${state:-$(detail)}"; fi

D="管理员用默认密码登录不了"
if nacos_login nacos nacos; then fail 2 "$D" "默认密码登录成功"; else pass 2 "$D"; fi

D="管理员用文件里的密码能登录"
if nacos_login nacos "$NACOS_ADMIN_PASSWORD"; then AT="$NACOS_TOKEN"; pass 3 "$D"; else fail 3 "$D" "HTTP $NACOS_HTTP"; fi
nacos_login "$NACOS_USERNAME" "$NACOS_PASSWORD" && APT="$NACOS_TOKEN"
nacos_login "$NACOS_PUSH_USERNAME" "$NACOS_PUSH_PASSWORD" && PT="$NACOS_TOKEN"

D="namespace 列表包含 dev、dev-secrets"
if [ -z "$AT" ]; then
  fail 4 "$D" "没有管理员令牌"
else
  nacos_api GET /v3/admin/core/namespace/list "$AT"
  if nacos_ok && jq -e '[.data[].namespace] | index("dev") and index("dev-secrets")' <<<"$NACOS_BODY" >/dev/null; then
    pass 4 "$D"
  else
    fail 4 "$D" "$(detail)，现有：$(jq -r '[.data[]?.namespace] | join(",")' <<<"$NACOS_BODY" 2>/dev/null)"
  fi
fi

D="patra-app 能读 dev、dev-secrets 的配置"
if [ -z "$APT" ]; then
  fail 5 "$D" "patra-app 登录失败"
elif [ -z "$AT" ]; then
  fail 5 "$D" "没有管理员令牌，发布不了探针"
else
  res=""
  for ns in dev dev-secrets; do
    cfg POST "$AT" "$ns" DEFAULT_GROUP "$PROBE"
    cfg GET "$APT" "$ns" DEFAULT_GROUP "$PROBE"
    if ! { nacos_ok && [ "$(jq -r '.data.content // empty' <<<"$NACOS_BODY")" = "probe: 1" ]; }; then
      res="$res $ns $(detail)"
    fi
  done
  if [ -z "$res" ]; then pass 5 "$D"; else fail 5 "$D" "读不到：$res"; fi
fi

D="patra-app 发布 dev、dev-secrets、public 配置都被拒"
if [ -z "$APT" ]; then
  fail 6 "$D" "patra-app 登录失败"
else
  cfg POST "$APT" dev DEFAULT_GROUP "$PROBE_PUSH"; r1="$NACOS_HTTP"
  cfg POST "$APT" dev-secrets DEFAULT_GROUP "$PROBE_PUSH"; r2="$NACOS_HTTP"
  cfg POST "$APT" public DEFAULT_GROUP "$PROBE_PUSH"; r3="$NACOS_HTTP"
  if [ "$r1" = 403 ] && [ "$r2" = 403 ] && [ "$r3" = 403 ]; then
    pass 6 "$D"
  else
    fail 6 "$D" "dev HTTP $r1，dev-secrets HTTP $r2，public HTTP $r3"
  fi
fi

D="patra-app 能在 public 注册、注销实例"
if [ -z "$APT" ]; then
  fail 7 "$D" "patra-app 登录失败"
else
  inst POST "$APT" public; ok1=no; nacos_ok && ok1=yes; d1="$(detail)"
  inst DELETE "$APT" public; ok2=no; nacos_ok && ok2=yes; d2="$(detail)"
  if [ "$ok1" = yes ] && [ "$ok2" = yes ]; then pass 7 "$D"; else fail 7 "$D" "注册 $d1，注销 $d2"; fi
fi

D="patra-app 在 dev 注册实例被拒"
if [ -z "$APT" ]; then
  fail 8 "$D" "patra-app 登录失败"
else
  inst POST "$APT" dev
  if [ "$NACOS_HTTP" = 403 ]; then pass 8 "$D"; else fail 8 "$D" "$(detail)"; fi
fi

D="patra-push 能发布、删除 dev/DEFAULT_GROUP 的配置"
if [ -z "$PT" ]; then
  fail 9 "$D" "patra-push 登录失败"
else
  cfg POST "$PT" dev DEFAULT_GROUP "$PROBE_PUSH"; ok1=no; nacos_ok && ok1=yes; d1="$(detail)"
  cfg DELETE "$PT" dev DEFAULT_GROUP "$PROBE_PUSH"; ok2=no; nacos_ok && ok2=yes; d2="$(detail)"
  if [ "$ok1" = yes ] && [ "$ok2" = yes ]; then pass 9 "$D"; else fail 9 "$D" "发布 $d1，删除 $d2"; fi
fi

# 导入、批量删除的鉴权取请求参数里的 groupName，不看实际操作的分组；克隆检查源 namespace 的读权限。
# 所以除了直接读写删，还要试这三条路径，按结果判断：探针内容不变、dev 里没有它的副本（spec 5.1）
D="patra-push 动不了 dev-secrets：读写删被拒，导入、批量删除、克隆无效"
if [ -z "$PT" ]; then
  fail 10 "$D" "patra-push 登录失败"
elif [ -z "$AT" ]; then
  fail 10 "$D" "没有管理员令牌，发布不了探针"
else
  cfg POST "$AT" dev-secrets DEFAULT_GROUP "$PROBE"
  cfg GET "$AT" dev-secrets DEFAULT_GROUP "$PROBE"
  id="$(jq -r '.data.id // empty' <<<"$NACOS_BODY" 2>/dev/null)"
  cfg GET "$PT" dev-secrets DEFAULT_GROUP "$PROBE"; r1="$NACOS_HTTP"
  cfg POST "$PT" dev-secrets DEFAULT_GROUP "$PROBE_PUSH"; r2="$NACOS_HTTP"
  cfg DELETE "$PT" dev-secrets DEFAULT_GROUP "$PROBE"; r3="$NACOS_HTTP"
  push_import dev-secrets; r4="$NACOS_HTTP"
  r5=跳过; r6=跳过
  if [ -n "$id" ]; then
    nacos_api DELETE /v3/admin/cs/config/batch "$PT" "ids=$id" namespaceId=dev groupName=DEFAULT_GROUP; r5="$NACOS_HTTP"
    push_clone "$id"; r6="$NACOS_HTTP"
  fi
  c="$(content dev-secrets "$PROBE")"
  leak="$(content dev "$PROBE_LEAK")"
  if [ -n "$id" ] && [ "$r1" = 403 ] && [ "$r2" = 403 ] && [ "$r3" = 403 ] \
    && [ "$c" = "probe: 1" ] && [ "$leak" = 不存在 ]; then
    pass 10 "$D"
  else
    fail 10 "$D" "读 HTTP $r1，写 $r2，删 $r3，导入 $r4，批量删除 $r5，克隆 $r6；探针内容 $c；dev 里的副本 $leak"
  fi
fi

if [ "$EXPECT_SERVICES" = true ]; then
  D="public 下 6 个 patra 服务都有实例"
  if [ -z "$APT" ]; then
    fail 11 "$D" "patra-app 登录失败"
  else
    missing=""
    for s in $SERVICES; do
      nacos_api GET /v3/admin/ns/instance/list "$APT" namespaceId=public groupName=DEFAULT_GROUP "serviceName=$s"
      if ! { nacos_ok && jq -e '.data | length > 0' <<<"$NACOS_BODY" >/dev/null; }; then missing="$missing $s"; fi
    done
    if [ -z "$missing" ]; then pass 11 "$D"; else fail 11 "$D" "没有实例：$missing"; fi
  fi
fi

echo "通过 $PASS / 失败 $FAIL"
if [ "$FAIL" -eq 0 ]; then exit 0; else exit 1; fi
