package dev.patra.analytics.infra.ingest.topic;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// 消费滞后 `analytics.ingest.lag`（标签 `topic`、`partition`）：每 30 秒用 AdminClient 对比消费组的已提交偏移量与末端偏移量。
///
/// 不用 Kafka 客户端自带的 `records-lag-max`：它在拉取时采样，ClickHouse 宕机、消费暂停期间不拉取，值变成 NaN，
/// 正好在最需要的时候看不到（入库设计 §7）。还没提交过的分区按「末端 − 最早」算（消费组从头读）。
/// 查询失败时保留上次的值、只记日志。
@Slf4j
@Component
@ConditionalOnProperty(name = "patra.analytics.ingest.enabled", havingValue = "true")
public class KafkaIngestLagGauge {

  /// 消费滞后指标
  static final String LAG_GAUGE = "analytics.ingest.lag";

  private static final long TIMEOUT_SECONDS = 10;

  private final Admin admin;
  private final MeterRegistry meters;
  private final String groupId;
  private final List<String> topics;
  private final Map<TopicPartition, Long> lags = new ConcurrentHashMap<>();

  /// 创建指标。
  ///
  /// @param admin Kafka 管理客户端
  /// @param meters 指标
  /// @param groupId 入库的消费组
  /// @param rawTopic 富化事件的 topic
  /// @param dlqTopic 不合格事件的 topic
  public KafkaIngestLagGauge(
      Admin admin,
      MeterRegistry meters,
      @Value("${patra.analytics.ingest.group-id}") String groupId,
      @Value("${patra.analytics.kafka.raw-topic:events.raw}") String rawTopic,
      @Value("${patra.analytics.kafka.dlq-topic:events.dlq}") String dlqTopic) {
    this.admin = admin;
    this.meters = meters;
    this.groupId = groupId;
    this.topics = List.of(rawTopic, dlqTopic);
  }

  /// 重新计算每个分区的滞后。
  @Scheduled(fixedDelayString = "30s", initialDelayString = "30s")
  public void refresh() {
    try {
      Map<TopicPartition, OffsetAndMetadata> committed =
          admin
              .listConsumerGroupOffsets(groupId)
              .partitionsToOffsetAndMetadata()
              .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      Set<TopicPartition> partitions =
          admin
              .describeTopics(topics)
              .allTopicNames()
              .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
              .values()
              .stream()
              .flatMap(
                  topic ->
                      topic.partitions().stream()
                          .map(info -> new TopicPartition(topic.name(), info.partition())))
              .collect(Collectors.toSet());
      Map<TopicPartition, Long> latest = offsets(partitions, OffsetSpec.latest());
      Map<TopicPartition, Long> earliest = offsets(partitions, OffsetSpec.earliest());
      for (TopicPartition partition : partitions) {
        OffsetAndMetadata done = committed.get(partition);
        long position = done != null ? done.offset() : earliest.getOrDefault(partition, 0L);
        long lag = Math.max(0, latest.getOrDefault(partition, position) - position);
        if (lags.put(partition, lag) == null) {
          register(partition);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.warn("刷新入库消费滞后失败，保留上次的值: {}", e.getMessage());
    }
  }

  /// 查一组分区的偏移量。
  ///
  /// @param partitions 分区
  /// @param spec 最早或末端
  /// @return 每个分区的偏移量
  private Map<TopicPartition, Long> offsets(Set<TopicPartition> partitions, OffsetSpec spec)
      throws Exception {
    Map<TopicPartition, OffsetSpec> request =
        partitions.stream().collect(Collectors.toMap(Function.identity(), partition -> spec));
    Map<TopicPartition, ListOffsetsResultInfo> result =
        admin.listOffsets(request).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    Map<TopicPartition, Long> offsets = new HashMap<>();
    result.forEach((partition, info) -> offsets.put(partition, info.offset()));
    return offsets;
  }

  /// 第一次见到的分区注册一个 gauge，读 `lags` 里的最新值。
  ///
  /// @param partition 分区
  private void register(TopicPartition partition) {
    Gauge.builder(LAG_GAUGE, lags, values -> values.getOrDefault(partition, 0L))
        .description("入库消费组落后末端的消息条数")
        .tag("topic", partition.topic())
        .tag("partition", String.valueOf(partition.partition()))
        .register(meters);
  }
}
