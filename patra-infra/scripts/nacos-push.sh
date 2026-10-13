#!/bin/bash
# 把 patra-infra/nacos/<namespace>/ 下的配置文件同步到 Nacos（PAP-75）。
#
# 用法：nacos-push.sh [--check] [目录]
#   目录      默认是本脚本旁边的 ../nacos/dev；目录名就是 namespace
#   --check   只对比，不写入。退出码 0 表示一致，2 表示有差异，1 表示出错
#
# 目录里的 *.yaml 就是 <namespace> / DEFAULT_GROUP 的全部配置：
#   内容不同或不存在的发布，相同的跳过，Nacos 里有、目录里没有的删除。
#   推送前先做本地预检：必须有 patra-common.yaml，每个文件都有完全匹配的签到行；
#   预检不过时不登录、不写入。
#
# 输入（环境变量）：
#   NACOS_ADDR                              默认 http://127.0.0.1:8848（mini 上的 runner 用默认值）
#   NACOS_PUSH_USERNAME、NACOS_PUSH_PASSWORD 推送账号；没设时读 $NACOS_SECRETS_DIR/nacos-push.env
#
# 设计：docs/patra/specs/2026-10-13-nacos-config-push-design.md
# 兼容 mini 的 bash 3.2；依赖 curl、jq（--rawfile 需要 1.6+）。
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=nacos-lib.sh source-path=SCRIPTDIR
source "$SCRIPT_DIR/nacos-lib.sh"

GROUP=DEFAULT_GROUP
COMMON=patra-common.yaml

CHECK=false
if [ "${1:-}" = "--check" ]; then
  CHECK=true
  shift
fi
DIR="${1:-$SCRIPT_DIR/../nacos/dev}"

# nacos_load 用间接展开读变量，set -u 下变量必须先存在
NACOS_PUSH_USERNAME="${NACOS_PUSH_USERNAME:-}"
NACOS_PUSH_PASSWORD="${NACOS_PUSH_PASSWORD:-}"

ADDED=0
UPDATED=0
DELETED=0
SAME=0

# die MSG：报错并以退出码 1 结束。
die() {
  echo "$*" >&2
  exit 1
}

# report 操作 DATAID：打印一行并计数。操作是 新增 / 更新 / 删除 / 未变化。
report() {
  echo "$1 $2"
  case "$1" in
    新增) ADDED=$((ADDED + 1)) ;;
    更新) UPDATED=$((UPDATED + 1)) ;;
    删除) DELETED=$((DELETED + 1)) ;;
    未变化) SAME=$((SAME + 1)) ;;
  esac
}

# preflight：本地预检，不联网。不通过时列出所有问题再退出。
preflight() {
  local file name kind bad=0
  [ -d "$DIR" ] || die "配置目录不存在：${DIR}"
  if [ ! -f "$DIR/$COMMON" ]; then
    echo "预检失败：缺少 ${COMMON}" >&2
    bad=1
  fi
  for file in ${FILES[@]+"${FILES[@]}"}; do
    name="$(basename "$file")"
    if [ "$name" = "$COMMON" ]; then kind=common; else kind=service; fi
    if ! grep -qxF "patra.config-source.${kind}: ${name}" "$file"; then
      echo "预检失败：${name} 缺少签到行「patra.config-source.${kind}: ${name}」" >&2
      bad=1
    fi
  done
  [ "$bad" = 0 ] || die "预检没通过，没有登录，也没有写入任何配置"
}

# login：用推送账号登录，令牌放进 NACOS_TOKEN。
login() {
  nacos_load NACOS_PUSH_USERNAME nacos-push.env || exit 1
  nacos_load NACOS_PUSH_PASSWORD nacos-push.env || exit 1
  if ! nacos_login "$NACOS_PUSH_USERNAME" "$NACOS_PUSH_PASSWORD"; then
    [ "$NACOS_HTTP" = 000 ] && die "连不上 Nacos：${NACOS_ADDR}"
    die "登录被拒：账号 ${NACOS_PUSH_USERNAME}（HTTP ${NACOS_HTTP}）"
  fi
}

# publish_files：逐个发布目录里的文件。
publish_files() {
  local file name action
  for file in ${FILES[@]+"${FILES[@]}"}; do
    name="$(basename "$file")"
    nacos_api GET /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
      "groupName=${GROUP}" "dataId=${name}"
    if nacos_ok; then
      # 逐字节比较：不经 shell 变量，命令替换会吞掉末尾换行
      if jq -e --rawfile want "$file" '.data.content == $want' <<<"$NACOS_BODY" >/dev/null; then
        report 未变化 "$name"
        continue
      fi
      action=更新
    elif [ "$NACOS_HTTP" = 404 ] && [ "$(nacos_code)" = 20004 ]; then
      action=新增
    else
      die "读取 ${name} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    fi
    nacos_api POST /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
      "groupName=${GROUP}" "dataId=${name}" type=yaml "content@${file}"
    nacos_ok || die "发布 ${name} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    report "$action" "$name"
  done
}

[ -d "$DIR" ] || die "配置目录不存在：${DIR}"
DIR="$(cd "$DIR" && pwd)"
NAMESPACE="$(basename "$DIR")"
shopt -s nullglob
FILES=("$DIR"/*.yaml)
shopt -u nullglob

preflight
login
publish_files
echo "新增 ${ADDED}，更新 ${UPDATED}，删除 ${DELETED}，未变化 ${SAME}"
exit 0
