package dev.patra.analytics.infra.collect.publish;

import dev.patra.analytics.domain.collect.exception.EventPublishUnavailableException;
import dev.patra.analytics.domain.collect.model.vo.OutboundMessage;
import dev.patra.analytics.domain.collect.port.publish.EventPublishPort;
import dev.patra.analytics.domain.kernel.model.enums.EventStream;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/// 把采集结果写进 Kafka（采集 spec §4.5）。
///
/// 整批一个总截止时间：逐条 `send()`，任何一条已经失败、或过了截止时间，就放弃剩下的、整批失败——Kafka 连不上时
/// 每次 `send()` 都可能卡满 `max.block.ms`，逐条等下去一批 500 条会卡上千秒。全部交出去后统一等确认，等到截止时间
/// 还没全部确认也整批失败。已经写成的那部分由产生方重试时按事件 ID 去重。
@Component
public class KafkaEventPublishAdapter implements EventPublishPort {

  /// 发送耗时指标
  static final String PUBLISH_TIMER = "analytics.collect.publish";

  private final KafkaTemplate<String, String> kafka;
  private final KafkaPublishProperties properties;
  private final Clock clock;
  private final Timer timer;

  /// 创建适配器。
  ///
  /// @param kafka Kafka 模板
  /// @param properties topic 与截止时间
  /// @param clock 时钟
  /// @param meterRegistry 指标
  public KafkaEventPublishAdapter(
      KafkaTemplate<String, String> kafka,
      KafkaPublishProperties properties,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.kafka = kafka;
    this.properties = properties;
    this.clock = clock;
    this.timer =
        Timer.builder(PUBLISH_TIMER).description("一批采集结果写进 Kafka 的耗时").register(meterRegistry);
  }

  /// 写出一批消息，全部确认后返回。
  ///
  /// @param messages 要写出的消息
  @Override
  public void publish(List<OutboundMessage> messages) {
    if (messages.isEmpty()) {
      return;
    }
    timer.record(() -> sendAll(messages));
  }

  /// 逐条发送，再统一等确认。
  ///
  /// @param messages 要写出的消息
  private void sendAll(List<OutboundMessage> messages) {
    Instant deadline = clock.instant().plus(properties.batchDeadline());
    List<CompletableFuture<SendResult<String, String>>> pending = new ArrayList<>(messages.size());
    for (OutboundMessage message : messages) {
      CompletableFuture<SendResult<String, String>> future;
      try {
        future = kafka.send(topicOf(message.stream()), message.key(), message.payload());
      } catch (RuntimeException e) {
        throw new EventPublishUnavailableException("写 Kafka 失败：" + e.getMessage(), e);
      }
      if (future.isCompletedExceptionally() || clock.instant().isAfter(deadline)) {
        throw new EventPublishUnavailableException("写 Kafka 失败或超过整批截止时间，放弃本批");
      }
      pending.add(future);
    }
    for (CompletableFuture<SendResult<String, String>> future : pending) {
      awaitUntil(future, deadline);
    }
  }

  /// 在截止时间前等一条消息的确认。
  ///
  /// @param future 发送结果
  /// @param deadline 截止时间
  private void awaitUntil(CompletableFuture<SendResult<String, String>> future, Instant deadline) {
    long remaining = Math.max(0, Duration.between(clock.instant(), deadline).toMillis());
    try {
      future.get(remaining, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      throw new EventPublishUnavailableException("Kafka 没有确认：" + e.getCause().getMessage(), e);
    } catch (TimeoutException e) {
      throw new EventPublishUnavailableException("等 Kafka 确认超过整批截止时间", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventPublishUnavailableException("等 Kafka 确认时被中断", e);
    }
  }

  /// 去向对应的 topic。
  ///
  /// @param stream 去向
  /// @return topic 名
  private String topicOf(EventStream stream) {
    return stream == EventStream.RAW ? properties.rawTopic() : properties.dlqTopic();
  }
}
