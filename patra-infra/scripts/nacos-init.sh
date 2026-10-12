#!/bin/bash
# Nacos 初始化：管理员、dev namespace、两个账号、角色和权限。幂等，可以反复执行。
# 用法（mini 上，仓库根目录，nacos 容器 healthy 之后）：
#   bash patra-infra/scripts/nacos-init.sh
# 输入：
#   NACOS_ADDR、NACOS_CONSOLE_ADDR、NACOS_SECRETS_DIR（见 nacos-lib.sh）
#   凭据：nacos-admin.env、nacos.env、nacos-push.env；环境变量里已有同名变量时优先，对应文件可以不存在
# 账号的权限以下面的 APP_PERMS、PUSH_PERMS 为准：缺的补上，多的删掉。不要在控制台手改权限，下次执行会被改回。
# 改账号密码：改文件后重跑本脚本，已有账号的密码会被重置成文件里的值。
# 管理员密码只在服务端还没有管理员时生效；之后要改，先在控制台改，再同步改 nacos-admin.env。
set -eo pipefail
# shellcheck source=nacos-lib.sh source-path=SCRIPTDIR
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/nacos-lib.sh"

APP_ROLE=PATRA_APP
PUSH_ROLE=PATRA_PUSH
# 每行一条权限：resource|action。资源串按 {namespace}:{group}:{config|naming}/{name} 做正则匹配
APP_PERMS='public:*:naming/*|rw
dev:*:config/*|r'
PUSH_PERMS='dev:DEFAULT_GROUP:config/*|rw
dev:*:config/*|r'

log() { echo "[nacos-init] $*"; }
die() { echo "[nacos-init] 失败：$*" >&2; exit 1; }
detail() { echo "HTTP $NACOS_HTTP code $(nacos_code)"; }

nacos_load NACOS_ADMIN_PASSWORD nacos-admin.env || exit 1
nacos_load NACOS_USERNAME nacos.env || exit 1
nacos_load NACOS_PASSWORD nacos.env || exit 1
nacos_load NACOS_PUSH_USERNAME nacos-push.env || exit 1
nacos_load NACOS_PUSH_PASSWORD nacos-push.env || exit 1

# ensure_user USERNAME PASSWORD：不存在就创建；存在就把密码重置成文件里的值
ensure_user() {
  nacos_api GET /v3/auth/user/list "$AT" pageNo=1 pageSize=100 "username=$1" search=accurate
  nacos_ok || die "查询用户 $1：$(detail)"
  if jq -e --arg u "$1" '.data.pageItems[]? | select(.username == $u)' <<<"$NACOS_BODY" >/dev/null; then
    nacos_api PUT /v3/auth/user "$AT" "username=$1" "newPassword=$2"
    nacos_ok || die "重置 $1 的密码：$(detail)"
    log "用户 $1 已存在，密码已按文件重置"
  else
    nacos_api POST /v3/auth/user "$AT" "username=$1" "password=$2"
    nacos_ok || die "创建用户 $1：$(detail)"
    log "用户 $1 已创建"
  fi
}

# ensure_role ROLE USERNAME：没绑定就绑定
ensure_role() {
  nacos_api GET /v3/auth/role/list "$AT" pageNo=1 pageSize=100 "username=$2" "role=$1" search=accurate
  nacos_ok || die "查询角色 $1：$(detail)"
  if jq -e --arg r "$1" --arg u "$2" '.data.pageItems[]? | select(.role == $r and .username == $u)' \
    <<<"$NACOS_BODY" >/dev/null; then
    log "角色 $1 已绑定 $2"
  else
    nacos_api POST /v3/auth/role "$AT" "role=$1" "username=$2"
    nacos_ok || die "绑定角色 $1 到 $2：$(detail)"
    log "角色 $1 已绑定到 $2"
  fi
}

# sync_perms ROLE DESIRED：让角色的权限与 DESIRED（每行 resource|action）完全一致
sync_perms() {
  local role="$1" desired="$2" current line added=0 removed=0
  nacos_api GET /v3/auth/permission/list "$AT" pageNo=1 pageSize=100 "role=$role" search=accurate
  nacos_ok || die "查询 $role 的权限：$(detail)"
  current="$(jq -r '.data.pageItems[]? | "\(.resource)|\(.action)"' <<<"$NACOS_BODY")"
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    if ! grep -Fxq -- "$line" <<<"$current"; then
      nacos_api POST /v3/auth/permission "$AT" "role=$role" "resource=${line%|*}" "action=${line##*|}"
      nacos_ok || die "给 $role 加权限 $line：$(detail)"
      added=$((added + 1))
    fi
  done <<<"$desired"
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    if ! grep -Fxq -- "$line" <<<"$desired"; then
      nacos_api DELETE /v3/auth/permission "$AT" "role=$role" "resource=${line%|*}" "action=${line##*|}"
      nacos_ok || die "删除 $role 的多余权限 $line：$(detail)"
      removed=$((removed + 1))
    fi
  done <<<"$current"
  log "角色 $role 的权限：新增 $added，删除 $removed"
}

log "等待 Nacos 就绪：$NACOS_CONSOLE_ADDR"
nacos_wait_ready || die "180 秒内没有就绪"

# 成功时响应体里有明文密码，只取 code，绝不打印响应体
nacos_api POST /v3/auth/user/admin "" "password=$NACOS_ADMIN_PASSWORD"
case "$(nacos_code)" in
  0) log "管理员 nacos 已初始化" ;;
  409) log "已有管理员，跳过初始化" ;;
  *) die "初始化管理员：$(detail)" ;;
esac

nacos_login nacos "$NACOS_ADMIN_PASSWORD" \
  || die "管理员登录（HTTP $NACOS_HTTP）。服务端已有管理员时，nacos-admin.env 要与它现在的密码一致"
AT="$NACOS_TOKEN"

nacos_api GET /v3/admin/core/namespace/check "$AT" namespaceId=dev
nacos_ok || die "查询 namespace dev：$(detail)"
if [ "$(jq -r '.data' <<<"$NACOS_BODY")" -gt 0 ]; then
  log "namespace dev 已存在"
else
  nacos_api POST /v3/admin/core/namespace "$AT" namespaceId=dev namespaceName=dev "namespaceDesc=Patra dev 环境"
  nacos_ok || die "创建 namespace dev：$(detail)"
  log "namespace dev 已创建"
fi

ensure_user "$NACOS_USERNAME" "$NACOS_PASSWORD"
ensure_user "$NACOS_PUSH_USERNAME" "$NACOS_PUSH_PASSWORD"
ensure_role "$APP_ROLE" "$NACOS_USERNAME"
ensure_role "$PUSH_ROLE" "$NACOS_PUSH_USERNAME"
sync_perms "$APP_ROLE" "$APP_PERMS"
sync_perms "$PUSH_ROLE" "$PUSH_PERMS"
log "完成"
