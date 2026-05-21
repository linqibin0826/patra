#!/usr/bin/env bash
# Install / uninstall LaunchDaemon: dev.patra.tailscale-route-guard
#
# 必须 root 运行。监听 tailnet peer 路由是否被错误劫持到非 Tailscale 接口，
# 检测到漂移自动 `route delete` + `tailscale down/up` 重新协商。
#
# Usage (注意必须 sudo):
#   sudo bash infra/scripts/install-tailscale-route-guard.sh install
#   sudo bash infra/scripts/install-tailscale-route-guard.sh uninstall
#   sudo bash infra/scripts/install-tailscale-route-guard.sh status

set -euo pipefail

LABEL="dev.patra.tailscale-route-guard"
SCRIPT_NAME="tailscale-route-guard.sh"

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
PLIST_SRC="${SRC_DIR}/${LABEL}.plist"
SCRIPT_SRC="${SRC_DIR}/${SCRIPT_NAME}"

PLIST_DST="/Library/LaunchDaemons/${LABEL}.plist"
SCRIPT_DST="/usr/local/sbin/${SCRIPT_NAME}"
LOG_FILE="/opt/homebrew/var/log/tailscale-route-guard.log"

require_root() {
    if [[ $EUID -ne 0 ]]; then
        echo "ERROR: 必须以 root 运行。请加 sudo：" >&2
        echo "  sudo bash $0 $*" >&2
        exit 1
    fi
}

cmd="${1:-status}"

case "$cmd" in
  install)
    require_root "$@"
    [[ -f "$PLIST_SRC" ]] || { echo "ERROR: plist 模板缺失: $PLIST_SRC" >&2; exit 1; }
    [[ -f "$SCRIPT_SRC" ]] || { echo "ERROR: 脚本模板缺失: $SCRIPT_SRC" >&2; exit 1; }
    # 前置：Tailscale 存在
    [[ -x /opt/homebrew/bin/tailscale ]] || { echo "ERROR: /opt/homebrew/bin/tailscale 不存在" >&2; exit 1; }

    # 旧 daemon 卸载（如有）
    if [[ -f "$PLIST_DST" ]]; then
        launchctl bootout system "$PLIST_DST" 2>/dev/null || true
    fi

    # 安装脚本
    mkdir -p "$(dirname "$SCRIPT_DST")"
    install -m 0755 -o root -g wheel "$SCRIPT_SRC" "$SCRIPT_DST"

    # 安装 plist
    install -m 0644 -o root -g wheel "$PLIST_SRC" "$PLIST_DST"

    # 准备日志目录与文件
    mkdir -p "$(dirname "$LOG_FILE")"
    touch "$LOG_FILE"
    chown root:wheel "$LOG_FILE"

    # 加载并启动
    launchctl bootstrap system "$PLIST_DST"
    launchctl enable "system/${LABEL}"
    sleep 2

    echo "✅ Installed and loaded: $LABEL"
    echo "   plist:  $PLIST_DST"
    echo "   script: $SCRIPT_DST"
    echo "   log:    $LOG_FILE"
    "$0" status
    ;;

  uninstall)
    require_root "$@"
    if [[ -f "$PLIST_DST" ]]; then
        launchctl bootout system "$PLIST_DST" 2>/dev/null || true
        rm -f "$PLIST_DST"
        echo "✅ Removed plist:  $PLIST_DST"
    fi
    if [[ -f "$SCRIPT_DST" ]]; then
        rm -f "$SCRIPT_DST"
        echo "✅ Removed script: $SCRIPT_DST"
    fi
    echo "✅ Uninstalled: $LABEL"
    echo "   (日志保留：$LOG_FILE)"
    ;;

  status)
    echo "==== launchctl print system/${LABEL} ===="
    launchctl print "system/${LABEL}" 2>/dev/null \
        | grep -E "state|last exit|program|pid" | head -10 \
        || echo "(not loaded)"
    echo ""
    echo "==== 当前 100.x 路由 ===="
    /usr/sbin/netstat -nrf inet 2>/dev/null | grep -E "^100\." || echo "(无)"
    echo ""
    echo "==== 守护日志（最后 10 行）===="
    [[ -f "$LOG_FILE" ]] && tail -n 10 "$LOG_FILE" || echo "($LOG_FILE 不存在)"
    ;;

  *)
    echo "Usage: sudo bash $0 {install|uninstall|status}" >&2
    exit 1
    ;;
esac
