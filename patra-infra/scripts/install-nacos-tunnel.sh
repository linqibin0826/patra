#!/usr/bin/env bash
# Install / uninstall launchd agent: dev.patra.nacos-tunnel
#
# 把 MacBook 上的 127.0.0.1:8848/9848/8080 通过 ssh tunnel 转发到 mac mini Nacos。
# 开机自动启动，断开自动重连（KeepAlive=true + ThrottleInterval=10）。
#
# 用途：让 Spring 应用以 NACOS_HOST=127.0.0.1（默认值）直连 Nacos，
# 绕过 tailscale wireguard MTU 1280 对 gRPC HTTP/2 handshake 的限制。
#
# Usage:
#   bash infra/scripts/install-nacos-tunnel.sh install      # 安装并启动
#   bash infra/scripts/install-nacos-tunnel.sh uninstall    # 停止并卸载
#   bash infra/scripts/install-nacos-tunnel.sh status       # 查看状态

set -euo pipefail

LABEL="dev.patra.nacos-tunnel"
PLIST_SRC="$(cd "$(dirname "$0")" && pwd)/${LABEL}.plist"
PLIST_DST="${HOME}/Library/LaunchAgents/${LABEL}.plist"
OUT_LOG="/tmp/patra-nacos-tunnel.out.log"
ERR_LOG="/tmp/patra-nacos-tunnel.err.log"

cmd="${1:-status}"

case "$cmd" in
  install)
    [ -f "$PLIST_SRC" ] || { echo "ERROR: plist 模板缺失: $PLIST_SRC" >&2; exit 1; }
    # 前置：确保能免密 ssh
    if ! ssh -o BatchMode=yes -o ConnectTimeout=5 linqibin@linqibins-mac-mini 'echo ok' &>/dev/null; then
      echo "ERROR: ssh linqibin@linqibins-mac-mini 不可达或未配免密。先做：" >&2
      echo "  ssh-copy-id linqibin@linqibins-mac-mini" >&2
      exit 1
    fi
    mkdir -p "$(dirname "$PLIST_DST")"
    cp "$PLIST_SRC" "$PLIST_DST"
    # 卸载旧的（如有），再加载
    launchctl unload "$PLIST_DST" 2>/dev/null || true
    launchctl load "$PLIST_DST"
    sleep 2
    echo "✅ Installed and loaded: $LABEL"
    echo "   plist: $PLIST_DST"
    echo "   logs:  $OUT_LOG / $ERR_LOG"
    "$0" status
    ;;
  uninstall)
    [ -f "$PLIST_DST" ] || { echo "No installation found at $PLIST_DST"; exit 0; }
    launchctl unload "$PLIST_DST" 2>/dev/null || true
    rm -f "$PLIST_DST"
    echo "✅ Uninstalled: $LABEL"
    ;;
  status)
    echo "==== launchctl list | grep $LABEL ===="
    launchctl list | grep "$LABEL" || echo "(not loaded)"
    echo ""
    echo "==== ssh tunnel process ===="
    pgrep -lf "linqibins-mac-mini" || echo "(no ssh tunnel process)"
    echo ""
    echo "==== port 8848 listener ===="
    lsof -nP -iTCP:8848 -sTCP:LISTEN 2>/dev/null | head -3 || echo "(8848 not listening)"
    echo ""
    echo "==== nacos health via tunnel ===="
    if curl -fsS --max-time 3 --noproxy '*' http://127.0.0.1:8080/v3/console/health/readiness 2>/dev/null; then
      echo " ✅ tunnel works"
    else
      echo " ❌ tunnel not reachable"
    fi
    ;;
  *)
    echo "Usage: $0 {install|uninstall|status}" >&2
    exit 1
    ;;
esac
