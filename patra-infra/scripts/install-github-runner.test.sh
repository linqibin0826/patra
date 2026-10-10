#!/usr/bin/env bash
# ============================================================================
# install-github-runner.sh 单测 —— HOME 指进沙箱，stub curl/mise/java 与 runner 自带的
# config.sh/svc.sh，不碰真实 runner。最新版固定为 2.338.0。
#   场景1 首次安装：带 token 跑 config.sh，.path 覆写回固定值，服务装好并启动
#   场景2 首次安装缺 token：报用法退出，curl/config.sh/svc.sh 一个都不调
#   场景3 已注册不带 token 即升级：不调 config.sh，换上新版，重写 .path/.env，服务重装启动
#   场景4 已注册却带 token：报错退出（防重新注册时 token 被静默忽略），不动服务
#   场景5 下载失败：服务不停（runner 照常在线），旧版原样
#   场景6 缺 Zulu 25：动服务之前就报错退出，服务不停，旧版原样
# 运行：bash patra-infra/scripts/install-github-runner.test.sh
# ============================================================================
# shellcheck disable=SC2016  # 断言用单引号是有意的：延迟到 check() 内 eval 时才展开
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PASS=0; FAIL=0
# shellcheck disable=SC2034  # 只在 check() 里 eval 的断言中使用
WANT_PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin"

setup() { # 每场景独立沙箱；导出全部 stub 控制变量
  TMP="$(mktemp -d)"
  RD="$TMP/home/actions-runner"
  export STUB_LOG="$TMP/calls.log" STUB_TARBALL="$TMP/release.tar.gz" STUB_SVC="$TMP/svc.sh" \
         STUB_JDK="$TMP/jdk" STUB_NO_JAVA="$TMP/no-java" STUB_DL_FAIL="$TMP/dl-fail"
  mkdir -p "$TMP/bin" "$TMP/home" "$TMP/release/bin" "$STUB_JDK/bin"
  : > "$STUB_LOG"

  cat > "$TMP/bin/curl" <<'EOF'
#!/usr/bin/env bash
echo "curl $*" >> "$STUB_LOG"
case "${!#}" in
  https://api.github.com/*) echo '  "tag_name": "v2.338.0",' ;;
  *.tar.gz)
    [ -e "$STUB_DL_FAIL" ] && exit 22
    while [ $# -gt 0 ]; do [ "$1" = -o ] && cp "$STUB_TARBALL" "$2"; shift; done ;;
esac
exit 0
EOF
  cat > "$TMP/bin/mise" <<'EOF'
#!/usr/bin/env bash
[ -e "$STUB_NO_JAVA" ] && exit 1
[ "$*" = "where java" ] && echo "$STUB_JDK"
EOF
  cat > "$STUB_JDK/bin/java" <<'EOF'
#!/usr/bin/env bash
echo 'openjdk version "25.0.1" 2025-10-21 LTS' >&2
echo 'OpenJDK Runtime Environment Zulu25.30+17-CA (build 25.0.1+8-LTS)' >&2
EOF
  cat > "$STUB_SVC" <<'EOF'
#!/usr/bin/env bash
echo "svc.sh $*" >> "$STUB_LOG"
EOF
  # 仿真真 config.sh：第一步 source env.sh，用当前 PATH 覆写 .path；已注册则报错退出；
  # 注册成功时生成 .runner 与 svc.sh
  cat > "$TMP/release/config.sh" <<'EOF'
#!/usr/bin/env bash
echo "config.sh $*" >> "$STUB_LOG"
echo "$PATH" > .path
if [ -f .runner ]; then
  echo "Cannot configure the runner because it is already configured." >&2
  exit 1
fi
echo macmini > .runner
cp "$STUB_SVC" svc.sh
EOF
  printf '#!/usr/bin/env bash\necho 2.338.0\n' > "$TMP/release/bin/Runner.Listener"
  chmod +x "$TMP/bin/curl" "$TMP/bin/mise" "$STUB_JDK/bin/java" "$STUB_SVC" \
           "$TMP/release/config.sh" "$TMP/release/bin/Runner.Listener"
  COPYFILE_DISABLE=1 tar czf "$STUB_TARBALL" -C "$TMP/release" .
  export PATH="$TMP/bin:$PATH"
}

registered() { # 已注册的 2.337.0：.runner 在，svc.sh 是当年 config.sh 生成的
  mkdir -p "$RD/bin"
  cp "$TMP/release/config.sh" "$STUB_SVC" "$RD/"
  printf '#!/usr/bin/env bash\necho 2.337.0\n' > "$RD/bin/Runner.Listener"
  chmod +x "$RD/bin/Runner.Listener"
  echo macmini > "$RD/.runner"
}

run() { # 以沙箱为 HOME 跑被测脚本，输出进 $TMP/out
  HOME="$TMP/home" bash "$SCRIPT_DIR/install-github-runner.sh" "$@" > "$TMP/out" 2>&1
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

# ---- 场景1：首次安装 ----
setup
run TKN; rc=$?
check "场景1 首次安装" 0 "$rc" \
  'grep -q "^config.sh .*--token TKN .*--disableupdate" "$STUB_LOG"' \
  '[ "$(cat "$RD/.path")" = "$WANT_PATH" ]' \
  'grep -qx "JAVA_HOME=$STUB_JDK" "$RD/.env"' \
  'grep -qx "svc.sh start" "$STUB_LOG"'

# ---- 场景2：首次安装缺 token ----
setup
run; rc=$?
check "场景2 首次安装缺 token" 1 "$rc" \
  'grep -q "用法" "$TMP/out"' \
  '[ ! -s "$STUB_LOG" ]'

# ---- 场景3：已注册，不带 token 即升级 ----
setup; registered
run; rc=$?
check "场景3 已注册不带 token 即升级" 0 "$rc" \
  '! grep -q "^config.sh" "$STUB_LOG"' \
  '[ "$("$RD/bin/Runner.Listener" --version)" = 2.338.0 ]' \
  'grep -qx "svc.sh stop" "$STUB_LOG"' \
  '[ "$(cat "$RD/.path")" = "$WANT_PATH" ]' \
  'grep -qx "JAVA_HOME=$STUB_JDK" "$RD/.env"' \
  'grep -qx "svc.sh start" "$STUB_LOG"'

# ---- 场景4：已注册却带 token ----
setup; registered
run TKN; rc=$?
check "场景4 已注册却带 token" 1 "$rc" \
  'grep -q "不需要 token" "$TMP/out"' \
  '! grep -qE "^(svc|config)\.sh" "$STUB_LOG"'

# ---- 场景5：下载失败 ----
setup; registered
touch "$STUB_DL_FAIL"
run; rc=$?
check "场景5 下载失败" 22 "$rc" \
  '! grep -q "^svc.sh" "$STUB_LOG"' \
  '[ "$("$RD/bin/Runner.Listener" --version)" = 2.337.0 ]'

# ---- 场景6：缺 Zulu 25 ----
setup; registered
touch "$STUB_NO_JAVA"
run; rc=$?
check "场景6 缺 Zulu 25" 1 "$rc" \
  'grep -q "未找到 mise 全局 java" "$TMP/out"' \
  '! grep -q "^svc.sh" "$STUB_LOG"' \
  '[ "$("$RD/bin/Runner.Listener" --version)" = 2.337.0 ]'

echo "----"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = 0 ]
