#!/usr/bin/env bash
# 建 Kafka topic，并把配置写成这里的值。由 docker-compose.pipeline.yaml 的 kafka-init 边车执行，
# 每次 `compose-all.sh up pipeline` 都会跑一遍：
#   - topic 不存在就建；
#   - 分区数只增不减：这里改大会扩容，改小报错退出（Kafka 不支持减少分区）；
#   - 保留策略每次都写一遍，手改过的配置会被改回这里的值。
#
# 「5 GB 上限」按整个 Kafka 算（spec 4.2 节）。retention.bytes 是每个分区的上限，且只删写满关闭的段文件，
# 所以一个分区最坏占用 = retention.bytes + segment.bytes（再加 broker 两次清理检查之间新写入的量）：
#   events.raw  3 × (1 GiB + 256 MiB) = 3.75 GiB
#   events.dlq  1 × (512 MiB + 128 MiB) = 0.625 GiB
# segment.ms 让流量小时段文件也按 6 小时滚动，否则一个段迟迟写不满，「保留 2 天」会拖成好几天。
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-patra-kafka:29092}"
BIN=/opt/kafka/bin
TWO_DAYS_MS=172800000
SIX_HOURS_MS=21600000

# 名称 分区数 retention.bytes（每分区） segment.bytes
TOPICS=(
  "events.raw 3 1073741824 268435456"
  "events.dlq 1 536870912 134217728"
)

for line in "${TOPICS[@]}"; do
  read -r name partitions retention_bytes segment_bytes <<<"${line}"
  echo "==> ${name}"

  "${BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --create --if-not-exists \
    --topic "${name}" --partitions "${partitions}" --replication-factor 1

  current="$("${BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --describe --topic "${name}" \
    | sed -n 's/.*PartitionCount: *\([0-9]*\).*/\1/p' | head -1)"
  if [ "${current}" -lt "${partitions}" ]; then
    echo "分区数 ${current} → ${partitions}"
    "${BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --alter \
      --topic "${name}" --partitions "${partitions}"
  elif [ "${current}" -gt "${partitions}" ]; then
    echo "错误: ${name} 现有 ${current} 个分区，脚本要求 ${partitions} 个。Kafka 不能减少分区，改回脚本里的数" >&2
    exit 1
  fi

  "${BIN}/kafka-configs.sh" --bootstrap-server "${BOOTSTRAP}" --alter \
    --entity-type topics --entity-name "${name}" \
    --add-config "cleanup.policy=delete,retention.ms=${TWO_DAYS_MS},retention.bytes=${retention_bytes},segment.bytes=${segment_bytes},segment.ms=${SIX_HOURS_MS}"
done

"${BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --describe --topics-with-overrides
echo "topic 初始化完成"
