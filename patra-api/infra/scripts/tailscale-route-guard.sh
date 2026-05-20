#!/usr/bin/env bash
# Tailscale route guard - 由 LaunchDaemon (root) 调用
#
# 解决问题:
#   macOS 上 Shadowrocket 拨断重连时，旧 utun 销毁、新 utun 创建，触发 link-change
#   事件。Tailscale daemon 应对 link-change 是 "先清空路由再重建"，中间有几百毫秒
#   空窗。空窗里去 100.x 的流量 fallback 到 default，被新的 Shadowrocket TUN 抢到，
#   macOS 缓存为克隆主机路由 (UHWIi)。/32 比 /10 更具体，Tailscale 之后再下发
#   100.64/10 → utun 也救不了缓存。
#
# 守护策略:
#   - 每 5 秒检查所有 tailnet peer IP 是否走 Tailscale 接口
#   - 检测到漂移 → 清除 100.64/10 内所有克隆主机路由 → `tailscale down/up` 重新协商
#   - 30 秒冷却防抖
#
# 不硬编码:
#   - Tailscale 的 utun 编号 (从 ifconfig 动态探测带 100.x 地址的 utun)
#   - peer IP 列表 (从 tailscale status 动态获取)

set -uo pipefail

LOG=/opt/homebrew/var/log/tailscale-route-guard.log
TAILSCALE=/opt/homebrew/bin/tailscale
INTERVAL=5
COOLDOWN=30
last_fix=0

# 所有 stdout/stderr 写日志（plist 的 StandardOutPath 重复保险）
exec >> "$LOG" 2>&1

log() { echo "$(date '+%F %T') $*"; }

# Tailscale 当前用的 utun 接口名（找带 100.x.x.x inet 的 utun，排除 magicdns）
ts_iface() {
    /sbin/ifconfig 2>/dev/null | awk '
        /^utun[0-9]+:/ { iface=$1; sub(":","",iface) }
        /^[[:space:]]+inet 100\./ && $2 !~ /^100\.100\.100\./ {
            print iface; exit
        }
    '
}

# tailnet peer IP 列表（排除自己 + magicdns）
peer_ips() {
    "$TAILSCALE" status 2>/dev/null \
        | awk 'NR>1 && $1 ~ /^100\./ && $1 !~ /^100\.100\.100\./ { print $1 }'
}

# 检测是否有 peer IP 漂移到非 Tailscale 接口
drift() {
    local expect="$1"
    [[ -z "$expect" ]] && return 1
    local got ip
    for ip in $(peer_ips); do
        got=$(/sbin/route -n get "$ip" 2>/dev/null | awk '/interface:/ { print $2 }')
        if [[ -n "$got" && "$got" != "$expect" ]]; then
            log "DRIFT: $ip -> $got (expected $expect)"
            return 0
        fi
    done
    return 1
}

# 修复：清克隆主机路由 + tailscale 重新协商
fix() {
    log "===== fix start ====="
    # 100.64/10 = 100.64.0.0 ~ 100.127.255.255
    local hr
    /usr/sbin/netstat -nrf inet 2>/dev/null \
        | awk '$1 ~ /^100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\./ && $4 ~ /UH/ { print $1 }' \
        | while read -r hr; do
            log "delete host route $hr"
            /sbin/route -n delete -host "$hr" 2>&1
        done
    log "tailscale down"
    "$TAILSCALE" down 2>&1
    sleep 1
    log "tailscale up --accept-dns=true"
    "$TAILSCALE" up --accept-dns=true 2>&1
    last_fix=$(date +%s)
    log "===== fix end ====="
}

trap 'log "guard stopping (signal)"; exit 0' SIGTERM SIGINT

log "guard started (pid $$, interval=${INTERVAL}s, cooldown=${COOLDOWN}s)"
while true; do
    sleep "$INTERVAL"
    now=$(date +%s)
    if (( now - last_fix < COOLDOWN )); then
        continue
    fi
    iface=$(ts_iface)
    if [[ -z "$iface" ]]; then
        # Tailscale daemon 还没起来 / 接口没准备好，安静等
        continue
    fi
    if drift "$iface"; then
        fix
    fi
done
