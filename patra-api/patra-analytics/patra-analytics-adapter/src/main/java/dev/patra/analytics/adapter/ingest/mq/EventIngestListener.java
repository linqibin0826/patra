package dev.patra.analytics.adapter.ingest.mq;

import dev.patra.analytics.app.ingest.usecase.ingest.IngestBatchCommand;
import dev.patra.analytics.domain.ingest.model.vo.IngestRecord;
import dev.patra.analytics.domain.ingest.port.topic.TopicIdPort;
import dev.patra.analytics.domain.kernel.model.enums.EventStream;
import dev.patra.tool.cqrs.CommandBus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/// 批量消费 `analytics.events.enriched` / `analytics.events.rejected`，整批交给入库用例，写成后提交偏移量。
///
/// 写入失败时抛出的是普通异常（不是 `BatchListenerFailedException`），错误处理器用同一批重试，令牌不变。
/// `id` 只用来找监听容器，消费组显式写 `groupId`（不写时 Spring 拿 `id` 当组名）。
///
/// 两个监听容器都不自动启动：boot 的 `IngestStartup` 建好 ClickHouse 的表之后才启动它们
/// （`patra.analytics.ingest.enabled` 为 `false` 时不启动，测试里默认关）。
@Component
@RequiredArgsConstructor
public class EventIngestListener {

  /// 富化事件的监听容器 ID
  public static final String ENRICHED_LISTENER_ID = "ingest-enriched";

  /// 不合格事件的监听容器 ID
  public static final String REJECTED_LISTENER_ID = "ingest-rejected";

  private final CommandBus commandBus;
  private final TopicIdPort topicIds;

  /// 消费富化事件。
  ///
  /// @param records 这一批
  /// @param ack 提交偏移量
  @KafkaListener(
      id = ENRICHED_LISTENER_ID,
      groupId = "${patra.analytics.ingest.group-id}",
      topics = "${patra.analytics.kafka.enriched-topic:analytics.events.enriched}",
      containerFactory = "ingestListenerFactory",
      autoStartup = "false")
  public void onEnriched(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
    ingest(EventStream.ENRICHED, records, ack);
  }

  /// 消费不合格事件。
  ///
  /// @param records 这一批
  /// @param ack 提交偏移量
  @KafkaListener(
      id = REJECTED_LISTENER_ID,
      groupId = "${patra.analytics.ingest.group-id}",
      topics = "${patra.analytics.kafka.rejected-topic:analytics.events.rejected}",
      containerFactory = "ingestListenerFactory",
      autoStartup = "false")
  public void onRejected(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
    ingest(EventStream.REJECTED, records, ack);
  }

  /// 转成命令写入，写成后提交。
  ///
  /// @param stream 来自哪个 topic
  /// @param records 这一批
  /// @param ack 提交偏移量
  private void ingest(
      EventStream stream, List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
    if (!records.isEmpty()) {
      String topicId = topicIds.topicId(records.getFirst().topic());
      commandBus.handle(
          IngestBatchCommand.of(
              stream,
              records.stream()
                  .map(r -> IngestRecord.of(topicId, r.partition(), r.offset(), r.value()))
                  .toList()));
    }
    ack.acknowledge();
  }
}
