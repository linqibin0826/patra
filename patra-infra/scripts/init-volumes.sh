#!/bin/bash
# Patra 基础设施 - 宿主机数据卷与配置初始化脚本
# ==============================================================
# 一次性运行，幂等（目录已存在则跳过，配置文件已存在则跳过）。
# 运行位置：patra 仓库根目录（Mac mini 上是 ~/Projects/patra）。
#
# 用法：
#   bash patra-infra/scripts/init-volumes.sh
#
# 放好密钥文件（见 docker/README.md「密钥」）后即可：
#   bash patra-infra/scripts/compose-all.sh up

set -euo pipefail

ROOT="${HOME}/.patra/docker"

echo "===================================="
echo "Patra 基础设施 - 数据卷与配置初始化"
echo "===================================="
echo ""
echo "数据根目录: $ROOT"
echo ""

# ---------------- 1. 目录骨架 ----------------
echo "[1/4] 创建目录骨架..."
mkdir -p \
  "$ROOT"/postgres/data \
  "$ROOT"/redis/data \
  "$ROOT"/mailpit \
  "$ROOT"/nacos/data \
  "$ROOT"/nacos/logs \
  "$ROOT"/rabbitmq/data \
  "$ROOT"/minio/data \
  "$ROOT"/es/data \
  "$ROOT"/xxl-job-admin/logs \
  "$ROOT"/mysql-ops/data \
  "$ROOT"/grafana/data \
  "$ROOT"/prometheus/data \
  "$ROOT"/loki/data \
  "$ROOT"/tempo/data \
  "$ROOT"/alertmanager/data
echo "  ✓ 目录骨架就绪"
echo ""

# ---------------- 2. redis.conf ----------------
REDIS_CONF="$ROOT/redis/redis.conf"
if [ -f "$REDIS_CONF" ]; then
  echo "[2/4] $REDIS_CONF 已存在，跳过"
else
  echo "[2/4] 创建 redis.conf..."
  cat > "$REDIS_CONF" <<'EOF'
bind 0.0.0.0
protected-mode no
port 6379
appendonly yes
appendfilename "appendonly.aof"
dir /data
EOF
  echo "  ✓ redis.conf 已创建"
fi
echo ""

# ---------------- 3. ES data 目录权限 ----------------
# Elasticsearch 容器以非 root UID 运行，需要写入 bind-mount 的宿主目录。
# 仅 chmod 目录本身（不加 -R），避免重复运行时改写已有数据文件权限。
echo "[3/4] 修正 Elasticsearch data 目录权限..."
chmod 777 "$ROOT/es/data"
echo "  ✓ ES data 权限就绪"
echo ""

# ---------------- 4. 密钥目录 ----------------
# 应用和 Redis 的密钥放在仓库外的 ~/.patra/secrets/（见 docker/README.md「密钥」）。
# 这里只建目录、收紧权限，不生成任何密钥。
SECRETS="${HOME}/.patra/secrets"
echo "[4/4] 准备密钥目录 ${SECRETS}..."
mkdir -p "$SECRETS"
chmod 700 "$SECRETS"
echo "  ✓ 密钥目录就绪（redis.env、rabbitmq.env、gateway.env 等要另外放进去）"
echo ""

echo "===================================="
echo "✅ 初始化完成"
echo "===================================="
echo ""
echo "下一步："
echo "  把 redis.env、rabbitmq.env、gateway.env 等放进 ~/.patra/secrets/（见 patra-infra/docker/README.md「密钥」）"
echo "  bash patra-infra/scripts/compose-all.sh up   # 拉起全部子栈（各为独立 project）"
echo "  bash patra-infra/scripts/compose-all.sh ps   # 查看所有 patra-* 容器"
echo ""
