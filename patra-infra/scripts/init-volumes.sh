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
echo "[1/5] 创建目录骨架..."
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
  echo "[2/5] $REDIS_CONF 已存在，跳过"
else
  echo "[2/5] 创建 redis.conf..."
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
  echo "[3/5] $BROKER_CONF 已存在，跳过"
else
  echo "[3/5] 创建 broker.conf..."
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
echo "[4/5] 修正 Elasticsearch data 目录权限..."
chmod 777 "$ROOT/es/data"
echo "  ✓ ES data 权限就绪"
echo ""

# ---------------- 5. 密钥目录 ----------------
# 应用和 Redis 的密钥放在仓库外的 ~/.patra/secrets/（见 docker/README.md「密钥」）。
# 这里只建目录、收紧权限，不生成任何密钥。
SECRETS="${HOME}/.patra/secrets"
echo "[5/5] 准备密钥目录 ${SECRETS}..."
mkdir -p "$SECRETS"
chmod 700 "$SECRETS"
echo "  ✓ 密钥目录就绪（redis.env、gateway.env 要另外放进去）"
echo ""

echo "===================================="
echo "✅ 初始化完成"
echo "===================================="
echo ""
echo "下一步："
echo "  把 redis.env、gateway.env 放进 ~/.patra/secrets/（见 patra-infra/docker/README.md「密钥」）"
echo "  bash patra-infra/scripts/compose-all.sh up   # 拉起全部子栈（各为独立 project）"
echo "  bash patra-infra/scripts/compose-all.sh ps   # 查看所有 patra-* 容器"
echo ""
