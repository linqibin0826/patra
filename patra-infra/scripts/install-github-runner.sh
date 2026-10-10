#!/usr/bin/env bash
# 在 Mac mini 上安装或升级 GitHub Actions self-hosted runner（launchd 常驻服务）。
# ============================================================
# 用法（在 Mac mini 上运行）：
#   首次安装：
#     1. GitHub 仓库 → Settings → Actions → Runners → New self-hosted runner，
#        复制其中的 registration token（约 1 小时有效）。
#     2. bash patra-infra/scripts/install-github-runner.sh <REGISTRATION_TOKEN>
#   升级（已注册，~/actions-runner/.runner 存在）：闲时不带参数重跑，跳过 config.sh，registration 不动
#     bash patra-infra/scripts/install-github-runner.sh
# runner labels：内置 self-hosted + 自定义 macmini（cd.yml 的 deploy job 据此选中）。
#
# 运维要点（2026-08-27 实战沉淀）：
#   - mini 出网必须经 Clash Verge（mixed-port 7897，常驻）；runner 是 launchd 服务、
#     不继承 shell 代理，故代理必须写进 $RUNNER_DIR/.env（本脚本固化）
#   - --disableupdate 关闭自更新：launchd 环境曾因下载走不了代理卡死自更新；
#     升级方式=闲时不带参数重跑本脚本（只更新二进制与 .path/.env，registration 不动）
#   - 派发任务期间严禁 ./svc.sh stop/start——会杀死执行中的 Worker，job 显示 cancelled；
#     重启前先 gh run list --status in_progress 确认为空
#   - runner 离线 >30 天 GitHub 自动删除 registration（2026-08 实际发生）；
#     runner-watchdog.yml 每日巡检兜底。重装=先删本地 .runner .credentials .credentials_rsaparams，
#     再重新取 registration token 跑本脚本
#   - CD 在本机跑 gradlew（构建原生 arm64 镜像），JAVA_HOME 指向 mise 全局 java。
#     两台 Mac 环境保持一致（用户约定）：Homebrew + brew 装 mise + zulu 25 全局钉版，前置：
#       brew install mise && mise install java@zulu-25.30.17.0 && mise use -g java@zulu-25.30.17.0
#     升级 JDK 时 MacBook 与 mini 一起升同一版本
set -euo pipefail

TOKEN="${1:-}"
REPO_URL="https://github.com/linqibin0826/patra"
RUNNER_DIR="$HOME/actions-runner"
ARCH="osx-arm64"           # Apple Silicon Mac mini

# 已注册（.runner 存在）= 升级，不需要 token；只有首次安装要
if [ -f "$RUNNER_DIR/.runner" ]; then
  if [ -n "$TOKEN" ]; then
    echo "⚠ runner 已注册：升级不需要 token，去掉参数重跑即可。" >&2
    echo "  要重新注册（如 registration 已被 GitHub 删除），先删掉 ${RUNNER_DIR} 里的" >&2
    echo "  .runner .credentials .credentials_rsaparams，再带 token 重跑。" >&2
    exit 1
  fi
else
  : "${TOKEN:?用法: install-github-runner.sh <REGISTRATION_TOKEN>（首次安装需要；已注册时不带参数即升级）}"
fi

mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"

# CD 构建要用 mise 全局 java（下面写进 .env 的 JAVA_HOME）：动服务之前先校验，免得停了服务才发现缺 JDK
MISE_BIN="$(command -v mise || echo /opt/homebrew/bin/mise)"
JH="$("$MISE_BIN" where java 2>/dev/null || true)"
if [ -z "$JH" ] || [ ! -x "$JH/bin/java" ]; then
  echo "⚠ 未找到 mise 全局 java（CD 构建需要；两台 Mac 同套管理）。先执行：" >&2
  echo "    brew install mise && mise install java@zulu-25.30.17.0 && mise use -g java@zulu-25.30.17.0" >&2
  echo "  再重跑本脚本。" >&2
  exit 1
fi
# 版本/发行版校验：CLAUDE.md 约定钉 Zulu 25，错版本 JDK 写进 .env 会让 CD 构建产物漂移
JAVA_VERSION_OUT="$("$JH/bin/java" -version 2>&1)"
if ! echo "$JAVA_VERSION_OUT" | grep -q 'openjdk version "25' || ! echo "$JAVA_VERSION_OUT" | grep -qi zulu; then
  echo "⚠ JAVA_HOME=$JH 不是 Zulu 25（实际: $(echo "$JAVA_VERSION_OUT" | head -1)）。" >&2
  echo "  mise use -g java@zulu-25.30.17.0 后重跑本脚本。" >&2
  exit 1
fi

# 运行时查 GitHub API 取最新 runner 版本（避免硬编码版本失效导致下载 404）
RUNNER_VERSION="$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest \
  | sed -nE 's/.*"tag_name": *"v([^"]+)".*/\1/p' | head -1)"
: "${RUNNER_VERSION:?无法从 GitHub API 解析 runner 版本（API 结构变更或限流），请稍后重试}"
echo "==> 最新 runner 版本: ${RUNNER_VERSION}"
TARBALL="actions-runner-${ARCH}-${RUNNER_VERSION}.tar.gz"

# 版本比对下载：新装或版本落后都会更新二进制（重跑本脚本即升级，配合 --disableupdate 的 30 天红线）
CURRENT_VERSION=""
[ -x ./bin/Runner.Listener ] && CURRENT_VERSION="$(./bin/Runner.Listener --version 2>/dev/null || true)"
if [ "$CURRENT_VERSION" != "$RUNNER_VERSION" ]; then
  echo "==> 下载 runner ${RUNNER_VERSION} (${ARCH})（当前: ${CURRENT_VERSION:-未安装}）"
  curl -fSL -o "$TARBALL" \
    "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/${TARBALL}"
  # 下载成功才停服务：下载失败时 runner 照常在线（新装时无 svc.sh；严禁在任务执行中重跑本脚本）
  [ -f ./svc.sh ] && ./svc.sh stop 2>/dev/null || true
  tar xzf "$TARBALL"
  rm -f "$TARBALL"
else
  echo "==> runner 已是最新 ${RUNNER_VERSION}，跳过下载"
fi

# 已注册就跳过 config.sh：它对已注册的 runner 直接报错退出（--replace 只管服务端的同名 runner），
# 而且第一步 source env.sh，会用当前 shell 的 PATH 覆写 .path
if [ -f .runner ]; then
  echo "==> runner 已注册，跳过 config.sh（registration 保留）"
else
  echo "==> 注册 runner 到 $REPO_URL"
  ./config.sh --url "$REPO_URL" --token "$TOKEN" \
    --name "macmini" --labels "macmini" --unattended --replace --disableupdate
fi

# 关键：非交互运行时 PATH 缺 OrbStack docker。config.sh 会按当前 PATH 生成 .path，
# 故必须在 config 之后覆写 .path 显式补 OrbStack docker 路径（/usr/local/bin），供 svc 读取。
echo "/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin" > .path

# launchd 服务不继承 shell 环境：代理与 JAVA_HOME 必须固化进 .env（runner 启动时读取）
cat > .env <<EOF
LANG=en_US.UTF-8
http_proxy=http://127.0.0.1:7897
https_proxy=http://127.0.0.1:7897
no_proxy=localhost,127.0.0.1,.local,100.64.0.0/10,192.168.0.0/16,nacos,postgres,redis
JAVA_HOME=$JH
EOF
echo "==> .env 已写入（代理 + JAVA_HOME=$JH）"

echo "==> 安装为 launchd 服务并启动"
./svc.sh uninstall 2>/dev/null || true   # 幂等：升级/重装时旧服务已存在，先卸再装
./svc.sh install
./svc.sh start
./svc.sh status
