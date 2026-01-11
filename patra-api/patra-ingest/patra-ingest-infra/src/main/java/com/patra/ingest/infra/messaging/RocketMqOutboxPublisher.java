package com.patra.ingest.infra.messaging;

import com.patra.ingest.domain.exception.OutboxPublishException;
import com.patra.ingest.domain.exception.OutboxPublishException.Reason;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.ingest.infra.config.OutboxMqProperties;
import com.patra.ingest.infra.messaging.config.RocketMqChannelMapper;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/// RocketMQ 发件箱发布器实现。
///
/// 使用 RocketMQ 官方 Spring Boot Starter 提供直接的 API 访问和高性能消息发布。
///
/// **架构特性**:
///
/// - 使用 {@link RocketMQTemplate} 进行消息发送,减少抽象层
///   - 通过 {@link RocketMqChannelMapper} 实现业务通道到技术 Topic 的映射,保持领域层纯净
///   - 支持顺序消息发送 (syncSendOrderly) 和普通消息发送 (syncSend)
///   - 正确分离 dedupKey (业务键) 和 partitionKey (分区键)
///
/// **职责**:
///
/// - 将发件箱消息发布到 RocketMQ
///   - 根据白名单验证通道许可
///   - 构建包含完整元数据的 RocketMQ 消息
///   - 处理发送失败并抛出领域异常
///
/// **消息元数据映射**:
///
/// - dedupKey → RocketMQ KEYS (用于消息追踪和去重)
///   - opType → RocketMQ TAGS (用于消费端过滤)
///   - partitionKey → hashKey (用于顺序消息的队列选择)
///   - headers → UserProperties (自定义属性)
///
/// @author linqibin
/// @since 0.1.0
/// @see RocketMqChannelMapper
@Slf4j
@Component
public class RocketMqOutboxPublisher implements OutboxPublisherPort {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final RocketMQTemplate rocketMQTemplate;
  private final RocketMqChannelMapper channelMapper;
  private final ObjectMapper objectMapper;
  private final OutboxMqProperties properties;

  public RocketMqOutboxPublisher(
      RocketMQTemplate rocketMQTemplate,
      RocketMqChannelMapper channelMapper,
      ObjectMapper objectMapper,
      OutboxMqProperties properties) {
    this.rocketMQTemplate = rocketMQTemplate;
    this.channelMapper = channelMapper;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public void publish(OutboxMessage message, RelayPlan plan) {
    // 1. 验证通道白名单
    String channel = message.getChannel();
    if (!properties.isChannelAllowed(channel)) {
      throw new OutboxPublishException(Reason.CHANNEL_NOT_ALLOWED, "通道不在白名单中: " + channel);
    }

    // 2. 业务通道映射到技术 Topic
    String topic = channelMapper.toTopic(channel);

    // 3. 构建 destination (topic:tags 格式)
    String destination = buildDestination(topic, message.getOpType());

    // 4. 构建 Spring 消息
    Message<String> springMsg = buildSpringMessage(message);

    // 5. 记录发送日志
    if (log.isDebugEnabled()) {
      log.debug(
          "正在发布 Outbox 消息到 RocketMQ, destination={} channel={} dedupKey={} partitionKey={}",
          destination,
          channel,
          message.getDedupKey(),
          message.getPartitionKey());
    }

    // 6. 发送消息 (根据是否有 partitionKey 选择普通或顺序发送)
    try {
      SendResult result;
      long timeout = properties.getSendTimeout();

      if (StringUtils.hasText(message.getPartitionKey())) {
        // 顺序消息发送: partitionKey 作为 hashKey,用于队列选择
        result =
            rocketMQTemplate.syncSendOrderly(
                destination,
                springMsg,
                message.getPartitionKey(), // hashKey 用于选择队列
                timeout // 使用配置的超时时间
                );
        log.debug("使用顺序发送模式, hashKey={} timeout={}ms", message.getPartitionKey(), timeout);
      } else {
        // 普通消息发送
        result = rocketMQTemplate.syncSend(destination, springMsg, timeout);
        log.debug("使用普通发送模式, timeout={}ms", timeout);
      }

      // 6. 检查发送结果
      if (result.getSendStatus() != SendStatus.SEND_OK) {
        throw new OutboxPublishException(
            Reason.SEND_FAILED,
            String.format(
                "RocketMQ 发送失败, status=%s msgId=%s topic=%s",
                result.getSendStatus(), result.getMsgId(), topic));
      }

      // 7. 记录成功日志（INFO 级别 - 关键业务事件）
      log.info(
          "Outbox 消息发送成功, outboxId={} rocketMsgId={} topic={} channel={} dedupKey={} queueId={} timeout={}ms",
          message.getId(),
          result.getMsgId(),
          topic,
          channel,
          message.getDedupKey(),
          result.getMessageQueue().getQueueId(),
          timeout);

    } catch (OutboxPublishException ex) {
      // 直接重新抛出业务异常,不要重新包装
      log.error(
          "Outbox 消息发送失败（业务异常）, outboxId={} topic={} channel={} dedupKey={} reason={}",
          message.getId(),
          topic,
          channel,
          message.getDedupKey(),
          ex.getReason());
      throw ex;
    } catch (Exception ex) {
      // 包装 RocketMQ 技术异常为业务异常
      log.error(
          "Outbox 消息发送失败（技术异常）, outboxId={} topic={} channel={} dedupKey={} error={}",
          message.getId(),
          topic,
          channel,
          message.getDedupKey(),
          ex.getMessage(),
          ex);
      throw new OutboxPublishException(Reason.SEND_FAILED, "发送消息到 RocketMQ 失败, topic=" + topic, ex);
    }
  }

  /// 构建 RocketMQ destination 字符串。
  ///
  /// 格式: "topic:tags"
  ///
  /// 注意：opType 是 NOT NULL，所以始终会添加 tags
  ///
  /// @param topic Topic 名称（粗粒度 Channel，如 INGEST_TASK）
  /// @param tags Tags（操作类型，如 READY、FAILED）
  /// @return destination 字符串（如 "INGEST_TASK:READY"）
  private String buildDestination(String topic, String tags) {
    // opType 是 NOT NULL，理论上始终有 tags
    if (StringUtils.hasText(tags)) {
      return topic + ":" + tags;
    }
    // 防御性代码：如果 tags 为空（不应该发生）
    return topic;
  }

  /// 构建 Spring 消息对象（用于 RocketMQTemplate）。
  ///
  /// **消息结构**:
  ///
  /// - Payload: JSON 字符串
  ///   - Header - KEYS: 对应 OutboxMessage.dedupKey (用于消息追踪和去重)
  ///   - Headers: 自定义头信息 + partitionKey + channel
  ///
  /// **注意**: TAGS 不通过 header 传递，而是通过 destination 参数 ("topic:tags" 格式)
  ///
  /// @param message 发件箱消息
  /// @return Spring 消息对象
  private Message<String> buildSpringMessage(OutboxMessage message) {

    // 1. 准备消息体（RocketMQTemplate 不支持空 payload，使用占位符）
    String payload = message.getPayloadJson();
    if (payload == null || payload.isEmpty()) {
      payload = "{}"; // 空 JSON 对象作为占位符
    }

    // 2. 创建 Spring Message Builder
    MessageBuilder<String> builder = MessageBuilder.withPayload(payload);

    // 3. 设置 RocketMQ KEYS (用于消息追踪和去重)
    builder.setHeader(RocketMQHeaders.KEYS, message.getDedupKey());

    // 4. 设置自定义属性 (headers)
    Map<String, Object> headers = parseHeaders(message.getHeadersJson());
    headers.forEach(builder::setHeader);

    // 5. 保存 partitionKey 到 headers (供消费端使用)
    if (StringUtils.hasText(message.getPartitionKey())) {
      builder.setHeader("partitionKey", message.getPartitionKey());
    }

    // 6. 添加业务通道信息 (便于消费端日志和监控)
    builder.setHeader("channel", message.getChannel());

    return builder.build();
  }

  /// 解析 JSON 格式的消息头。
  ///
  /// @param headersJson JSON 字符串
  /// @return 消息头 Map
  private Map<String, Object> parseHeaders(String headersJson) {
    if (!StringUtils.hasText(headersJson)) {
      return Collections.emptyMap();
    }
    try {
      return objectMapper.readValue(headersJson, MAP_TYPE);
    } catch (JacksonException ex) {
      throw new OutboxPublishException(Reason.HEADERS_INVALID, "解析 Outbox headers JSON 失败", ex);
    }
  }
}
