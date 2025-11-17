#!/bin/bash
# RocketMQ 智能启动脚本 - 自动检测宿主机 IP 并启动服务
# 适用于需要在不同网络环境（工作/家庭）间切换的场景

set -e  # 遇到错误立即退出

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

echo "===================================="
echo "🚀 RocketMQ 智能启动"
echo "===================================="
echo ""

# 检测宿主机局域网 IP
echo "🔍 正在检测宿主机局域网 IP..."

# 获取所有局域网 IP（排除 127.0.0.1 和虚拟网卡）
LAN_IPS=$(ifconfig | grep -A 2 "inet " | grep -v "127.0.0.1" | grep "inet" | awk '{print $2}' | grep -E "^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)")

if [ -z "$LAN_IPS" ]; then
    echo "❌ 未检测到有效的局域网 IP 地址"
    echo "请确保你的 Mac 已连接到 WiFi 或以太网"
    exit 1
fi

# 显示所有检测到的 IP
echo "检测到以下局域网 IP："
echo "$LAN_IPS" | nl -w2 -s'. '
echo ""

# 选择第一个 IP（通常是主网络接口）
DETECTED_IP=$(echo "$LAN_IPS" | head -1)

# 如果检测到多个 IP，让用户选择
IP_COUNT=$(echo "$LAN_IPS" | wc -l | tr -d ' ')
if [ "$IP_COUNT" -gt 1 ]; then
    echo "⚠️  检测到多个网络接口，将使用: $DETECTED_IP"
    echo "   如需使用其他 IP，请手动编辑 .env 文件"
else
    echo "✅ 使用 IP: $DETECTED_IP"
fi

echo ""

# 更新 .env 文件
echo "📝 更新 .env 配置..."
if [ -f "$ENV_FILE" ]; then
    # 使用 sed 更新 BROKER_IP1（兼容 macOS 和 Linux）
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s/^BROKER_IP1=.*/BROKER_IP1=$DETECTED_IP/" "$ENV_FILE"
    else
        # Linux
        sed -i "s/^BROKER_IP1=.*/BROKER_IP1=$DETECTED_IP/" "$ENV_FILE"
    fi
    echo "✅ 已更新 .env 文件: BROKER_IP1=$DETECTED_IP"
else
    echo "❌ 未找到 .env 文件: $ENV_FILE"
    exit 1
fi

echo ""
echo "🐳 启动 RocketMQ 服务..."
echo ""

# 重新创建 RocketMQ Broker（确保使用新的 IP）
docker compose -p patra -f "$SCRIPT_DIR/docker-compose.jobs.yaml" down rocketmq-broker 2>/dev/null || true
docker compose -p patra -f "$SCRIPT_DIR/docker-compose.jobs.yaml" up -d rocketmq-broker

echo ""
echo "===================================="
echo "✅ RocketMQ 启动完成！"
echo "===================================="
echo ""
echo "📊 RocketMQ 信息："
echo "   Broker IP: $DETECTED_IP"
echo "   NameServer: localhost:9876"
echo "   Dashboard: http://localhost:8080"
echo ""
echo "📝 提示："
echo "   - 切换网络环境后，重新运行此脚本即可自动适配新 IP"
echo "   - 查看日志: docker logs -f patra-rocketmq-broker"
echo "   - 停止服务: docker compose -p patra -f docker/docker-compose.jobs.yaml down rocketmq-broker"
echo ""
