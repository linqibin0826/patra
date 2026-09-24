#!/usr/bin/env bash
# Patra 基础设施 - 多 project 编排入口
# ==============================================================
# 各子栈是独立 Docker project（patra-core / patra-storage / patra-search /
# patra-observability / patra-tailnet / patra-jobs），共享外部网络 patra-net。
# 这样 IDEA / OrbStack 的 Docker 视图按 project 把各子栈分组显示，而非合成一个 patra 组。
#
# 取代旧的 docker-compose.dev.yaml include 单项目模型——compose 的 include 会把
# 所有子栈合并进同一个 project，无法分组；多 project 只能逐个 up，故用本脚本编排。
#
# 用法（在仓库根目录运行）：
#   bash patra-infra/scripts/compose-all.sh up        # 拉起全部子栈
#   bash patra-infra/scripts/compose-all.sh up core   # 只拉起 core
#   bash patra-infra/scripts/compose-all.sh down       # 停全部（逆序，不删共享网络）
#   bash patra-infra/scripts/compose-all.sh ps         # 查看所有 patra-* 容器
set -euo pipefail

DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../docker" && pwd)"

# 启动顺序：core 基础设施先行；tailnet 网关先于 jobs（jobs 的路由边车 getent 解析 tailscale-gw）。
ORDER=(core storage search observability tailnet jobs apps)

compose() { docker compose -f "$DOCKER_DIR/docker-compose.$1.yaml" "${@:2}"; }

ensure_network() {
  # 外部网络须先于任何子栈存在（compose 声明 external: true，不会自动创建）。幂等。
  # 网段写死：tailscale-gw 把它作为子网路由发布到 tailnet（TS_ROUTES），网段变了 MacBook 就连不上容器。
  docker network inspect patra-net >/dev/null 2>&1 \
    || docker network create --subnet 192.168.97.0/24 patra-net
}

# 校验子栈参数：未知名直接报错退出非 0，避免拼写错误导致静默空执行（up/down 什么都不做却返回成功）。
require_valid_stacks() {
  for want in "$@"; do
    case " ${ORDER[*]} " in
      *" $want "*) ;;
      *) echo "错误: 未知子栈 '$want'。可选: ${ORDER[*]}" >&2; exit 1 ;;
    esac
  done
}

# 选择要操作的子栈：未指定则全部，否则取参数（保持 ORDER 的相对顺序）。
select_stacks() {
  if [ "$#" -eq 0 ]; then printf '%s\n' "${ORDER[@]}"; return; fi
  for s in "${ORDER[@]}"; do for want in "$@"; do [ "$s" = "$want" ] && echo "$s"; done; done
}

ACTION="${1:-}"; shift || true

case "$ACTION" in
  up)
    require_valid_stacks "$@"
    ensure_network
    while IFS= read -r s; do
      echo "==> up patra-$s"
      compose "$s" up -d --remove-orphans
    done < <(select_stacks "$@")
    ;;
  down)
    require_valid_stacks "$@"
    # 逆序停。patra-net 是 external，compose 不会删它（其它 project 可能仍在用）。
    while IFS= read -r s; do echo "$s"; done < <(select_stacks "$@") \
      | tail -r | while IFS= read -r s; do
          echo "==> down patra-$s"
          compose "$s" down
        done
    ;;
  ps)
    docker ps --filter 'name=patra-' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
    ;;
  *)
    echo "用法: $0 {up|down|ps} [stack...]   stack ∈ {${ORDER[*]}}" >&2
    exit 1
    ;;
esac
