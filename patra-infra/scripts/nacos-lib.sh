#!/bin/bash
# nacos-init.sh、nacos-verify.sh、nacos-push.sh 共用的函数。只供 source，不单独执行。
#
# 输入（环境变量，都有默认值）：
#   NACOS_ADDR          Nacos HTTP 地址，默认 http://127.0.0.1:8848（不用 localhost：容器和本机都可能先解析到 ::1）
#   NACOS_CONSOLE_ADDR  控制台地址（readiness 在这里），默认 http://127.0.0.1:8080
#   NACOS_SECRETS_DIR   密钥文件目录，默认 ~/.patra/secrets
#
# 约定（docs/patra/specs/2026-10-11-nacos-hardening-design.md 11.1）：
#   - 令牌只放 Authorization 请求头；密码、配置正文只走 POST/PUT 的表单请求体；
#     GET/DELETE 的参数进 URL 查询串，只能放不敏感的值（镜像开着 access log，会记录查询串）。
#   - 登录接口没有 code，看 HTTP 200 且 accessToken 非空；其余接口看响应体 code 为 0。
#     很多错误的 HTTP 状态码仍是 200，只看状态码会漏判。
#   - 不打印密码、令牌和响应体。
#   - 兼容 mini 的 bash 3.2。

NACOS_ADDR="${NACOS_ADDR:-http://127.0.0.1:8848}"
NACOS_CONSOLE_ADDR="${NACOS_CONSOLE_ADDR:-http://127.0.0.1:8080}"
NACOS_SECRETS_DIR="${NACOS_SECRETS_DIR:-$HOME/.patra/secrets}"

# nacos_load VAR FILE：环境变量 VAR 已有值就用它；否则从 $NACOS_SECRETS_DIR/FILE 读 VAR=... 这一行。
# 读不到返回 1，并说明缺什么。
nacos_load() {
  local var="$1" file="$NACOS_SECRETS_DIR/$2" val
  [ -n "${!var}" ] && return 0
  if [ ! -f "$file" ]; then
    echo "缺少 $var：环境变量没有设置，$file 也不存在" >&2
    return 1
  fi
  val="$(sed -n "s/^$var=//p" "$file" | tail -n 1)"
  if [ -z "$val" ]; then
    echo "缺少 $var：$file 里没有这一行，或者值为空" >&2
    return 1
  fi
  printf -v "$var" '%s' "$val"
}

# nacos_api METHOD PATH TOKEN [key=value ...]
# PATH 不含 /nacos 前缀，例如 /v3/auth/user。TOKEN 为空时不带 Authorization。
# 结果放在 NACOS_HTTP（状态码，连不上时是 000）和 NACOS_BODY（响应体）。总是返回 0，由调用方判断。
nacos_api() {
  local method="$1" path="$2" token="$3" kv out
  shift 3
  local args=(-sS --noproxy '*' -m 15 -X "$method" -w '\n%{http_code}')
  case "$method" in GET | DELETE) args+=(-G) ;; esac
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  for kv in "$@"; do args+=(--data-urlencode "$kv"); done
  if out="$(curl "${args[@]}" "$NACOS_ADDR/nacos$path" 2>/dev/null)"; then
    NACOS_HTTP="${out##*$'\n'}"
    NACOS_BODY="${out%$'\n'*}"
  else
    NACOS_HTTP=000
    NACOS_BODY=""
  fi
  return 0
}

# nacos_code：输出 NACOS_BODY 里的 code；不是 JSON 时输出空。
nacos_code() { jq -r '.code // empty' <<<"$NACOS_BODY" 2>/dev/null; }

# nacos_ok：上一次请求 HTTP 200 且 code 为 0。
nacos_ok() { [ "$NACOS_HTTP" = 200 ] && [ "$(nacos_code)" = 0 ]; }

# nacos_login USER PASSWORD：成功时把令牌放进 NACOS_TOKEN 并返回 0。
nacos_login() {
  NACOS_TOKEN=""
  nacos_api POST /v3/auth/user/login "" "username=$1" "password=$2"
  [ "$NACOS_HTTP" = 200 ] || return 1
  NACOS_TOKEN="$(jq -r '.accessToken // empty' <<<"$NACOS_BODY" 2>/dev/null)"
  [ -n "$NACOS_TOKEN" ]
}

# nacos_wait_ready [秒数]：等控制台 readiness 返回 200，默认最多 180 秒。
nacos_wait_ready() {
  local limit="${1:-180}" waited=0
  until curl -fsS --noproxy '*' -m 5 -o /dev/null "$NACOS_CONSOLE_ADDR/v3/console/health/readiness" 2>/dev/null; do
    waited=$((waited + 3))
    [ "$waited" -ge "$limit" ] && return 1
    sleep 3
  done
}
