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
PAGE_SIZE=100

# usage_error MSG：报参数错误和用法，以退出码 1 结束。
usage_error() {
  echo "$1" >&2
  echo "用法：nacos-push.sh [--check] [目录]" >&2
  exit 1
}

CHECK=false
DIR=""
for arg in "$@"; do
  case "$arg" in
    --check) CHECK=true ;;
    -*) usage_error "不认识的参数：${arg}" ;;
    *)
      [ -z "$DIR" ] || usage_error "只能给一个目录：${DIR}、${arg}"
      DIR="$arg"
      ;;
  esac
done
DIR="${DIR:-$SCRIPT_DIR/../nacos/dev}"

# nacos_load 用间接展开读变量，set -u 下变量必须先存在
NACOS_PUSH_USERNAME="${NACOS_PUSH_USERNAME:-}"
NACOS_PUSH_PASSWORD="${NACOS_PUSH_PASSWORD:-}"

ADDED=0
UPDATED=0
DELETED=0
SAME=0

LISTING="$(mktemp)"
trap 'rm -f "$LISTING"' EXIT

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
    case "$NACOS_HTTP" in
      000) die "连不上 Nacos：${NACOS_ADDR}" ;;
      401 | 403) die "登录被拒：账号 ${NACOS_PUSH_USERNAME}（HTTP ${NACOS_HTTP}）" ;;
      *) die "Nacos 地址不对或不是 Nacos 接口：${NACOS_ADDR}（HTTP ${NACOS_HTTP}）" ;;
    esac
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
    if [ "$CHECK" = false ]; then
      nacos_api POST /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
        "groupName=${GROUP}" "dataId=${name}" type=yaml "content@${file}"
      nacos_ok || die "发布 ${name} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    fi
    report "$action" "$name"
  done
}

# remote_data_ids：把 NAMESPACE / GROUP 下的全部 dataId 逐行写到 stdout，分页取完。
# 调用方用重定向（不用命令替换），出错时 die 才能结束整个脚本。
remote_data_ids() {
  local page=1 pages
  while :; do
    nacos_api GET /v3/admin/cs/config/list "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
      "groupName=${GROUP}" dataId= "pageNo=${page}" "pageSize=${PAGE_SIZE}" search=accurate
    nacos_ok || die "列出 ${NAMESPACE} / ${GROUP} 的配置失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    jq -r '.data.pageItems[].dataId' <<<"$NACOS_BODY"
    pages="$(jq -r '.data.pagesAvailable' <<<"$NACOS_BODY")"
    [ "$page" -ge "$pages" ] && return 0
    page=$((page + 1))
  done
}

# prune：删除 Nacos 里有、目录里没有的配置。先列完再删，避免边删边翻页漏掉。
prune() {
  local id file names=""
  for file in ${FILES[@]+"${FILES[@]}"}; do
    names="${names}$(basename "$file")"$'\n'
  done
  remote_data_ids >"$LISTING"
  while IFS= read -r id; do
    [ -z "$id" ] && continue
    # 用 here-string 不用管道：pipefail 下 grep -q 提前退出可能让 printf 收到 SIGPIPE，
    # 整条管道判为失败，本地有的配置会被当成没有而误删
    grep -qxF -- "$id" <<<"$names" && continue
    if [ "$CHECK" = false ]; then
      nacos_api DELETE /v3/admin/cs/config "$NACOS_TOKEN" "namespaceId=${NAMESPACE}" \
        "groupName=${GROUP}" "dataId=${id}"
      nacos_ok || die "删除 ${id} 失败：HTTP ${NACOS_HTTP}，code $(nacos_code)"
    fi
    report 删除 "$id"
  done <"$LISTING"
}

[ -d "$DIR" ] || die "配置目录不存在：${DIR}"
DIR="$(cd "$DIR" && pwd)"
NAMESPACE="$(basename "$DIR")"
shopt -s nullglob
FILES=("$DIR"/*.yaml)
shopt -u nullglob

preflight
login
if [ "$CHECK" = true ]; then
  echo "只对比，不写入（--check）"
fi
publish_files
prune
echo "新增 ${ADDED}，更新 ${UPDATED}，删除 ${DELETED}，未变化 ${SAME}"
if [ "$CHECK" = true ] && [ $((ADDED + UPDATED + DELETED)) -gt 0 ]; then
  exit 2
fi
exit 0
