#!/bin/bash
# Patra 基础设施 - 宿主机数据卷与配置初始化脚本
# ==============================================================
# 一次性运行，幂等（目录已存在则跳过，配置文件已存在则跳过）。
# 运行位置：git clone 后的 patra-api 仓库根目录。
#
# 用法：
#   bash infra/scripts/init-volumes.sh
#
# 完成后即可：
#   docker compose -f infra/docker/docker-compose.dev.yaml up -d

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
  "$ROOT"/nacos/data \
  "$ROOT"/nacos/logs \
  "$ROOT"/minio/data \
  "$ROOT"/es/data \
  "$ROOT"/rocketmq/namesrv/logs \
  "$ROOT"/rocketmq/namesrv/store \
  "$ROOT"/rocketmq/broker/logs \
  "$ROOT"/rocketmq/broker/store \
  "$ROOT"/rocketmq/broker/conf \
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

# ---------------- 3. broker.conf ----------------
BROKER_CONF="$ROOT/rocketmq/broker/conf/broker.conf"
if [ -f "$BROKER_CONF" ]; then
  echo "[3/4] $BROKER_CONF 已存在，跳过"
else
  echo "[3/4] 创建 broker.conf..."
  cat > "$BROKER_CONF" <<'EOF'
brokerClusterName = PatraCluster
brokerName = broker-a
brokerId = 0
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH
EOF
  echo "  ✓ broker.conf 已创建"
fi
echo ""

# ---------------- 4. ES data 目录权限 ----------------
# Elasticsearch 容器以非 root UID 运行，需要写入 bind-mount 的宿主目录。
# 仅 chmod 目录本身（不加 -R），避免重复运行时改写已有数据文件权限。
echo "[4/4] 修正 Elasticsearch data 目录权限..."
chmod 777 "$ROOT/es/data"
echo "  ✓ ES data 权限就绪"
echo ""

echo "===================================="
echo "✅ 初始化完成"
echo "===================================="
echo ""
echo "下一步："
echo "  docker compose -f docker/docker-compose.dev.yaml up -d"
echo "  docker compose -f docker/docker-compose.dev.yaml ps"
echo ""
