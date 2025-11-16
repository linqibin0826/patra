package com.patra.ingest.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.exception.OutboxPublishException;
import com.patra.ingest.domain.exception.OutboxPublishException.Reason;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import com.patra.ingest.infra.config.OutboxMqProperties;
import com.patra.ingest.infra.messaging.config.RocketMqChannelMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;

/**
 * RocketMqOutboxPublisher 单元测试。
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>✅ 正常消息发送 (无 partitionKey)
 *   <li>✅ 顺序消息发送 (有 partitionKey)
 *   <li>✅ 通道白名单验证
 *   <li>✅ 发送失败处理 (SendStatus 不是 SEND_OK)
 *   <li>✅ 异常处理 (RocketMQ 抛出异常)
 *   <li>✅ Headers 解析正常
 *   <li>✅ Headers 解析失败 (无效 JSON)
 *   <li>✅ 消息元数据映射 (dedupKey → KEYS, opType → TAGS, partitionKey → UserProperty)
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RocketMqOutboxPublisher 单元测试")
class RocketMqOutboxPublisherTest {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  @Mock private RocketMQTemplate rocketMQTemplate;

  @Mock private RocketMqChannelMapper channelMapper;

  @Mock private ObjectMapper objectMapper;

  @Mock private OutboxMqProperties properties;

  @Captor private ArgumentCaptor<Message> messageCaptor;

  @Captor private ArgumentCaptor<String> topicCaptor;

  @Captor private ArgumentCaptor<String> hashKeyCaptor;

  private RocketMqOutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    // Mock 默认配置值 (使用 lenient 避免 UnnecessaryStubbingException)
    lenient().when(properties.getSendTimeout()).thenReturn(3000);

    publisher =
        new RocketMqOutboxPublisher(rocketMQTemplate, channelMapper, objectMapper, properties);
  }

  @Nested
  @DisplayName("普通消息发送场景 (无 partitionKey)")
  class NormalMessageSendingTests {

    @Test
    @DisplayName("应成功发送普通消息并正确映射元数据")
    void shouldSendNormalMessageSuccessfully() throws JsonProcessingException {
      // Given: 准备测试数据
      String channel = "TASK_READY";
      String topic = "INGEST_TASK_READY";
      String dedupKey = "task-123";
      String opType = "TASK_READY";
      String payloadJson = "{\"taskId\":\"123\"}";
      String headersJson = "{\"source\":\"scheduler\"}";

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(1L)
              .channel(channel)
              .opType(opType)
              .dedupKey(dedupKey)
              .payloadJson(payloadJson)
              .headersJson(headersJson)
              // partitionKey 使用默认值空字符串 (无 partitionKey)
              .build();

      RelayPlan plan = createTestRelayPlan();

      // Mock 通道白名单验证通过
      when(properties.isChannelAllowed(channel)).thenReturn(true);

      // Mock 通道到 Topic 映射
      when(channelMapper.toTopic(channel)).thenReturn(topic);

      // Mock headers 解析
      Map<String, Object> headers = Map.of("source", "scheduler");
      when(objectMapper.readValue(eq(headersJson), any(TypeReference.class))).thenReturn(headers);

      // Mock 发送成功 (注意: destination 格式为 "topic:tags")
      SendResult sendResult = createSuccessSendResult(topic, "msg-001", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When: 发送消息
      publisher.publish(message, plan);

      // Then: 验证使用 syncSend (普通发送)
      verify(rocketMQTemplate).syncSend(topicCaptor.capture(), messageCaptor.capture(), eq(3000L));
      verify(rocketMQTemplate, never())
          .syncSendOrderly(anyString(), any(Message.class), anyString(), eq(3000L));

      // 验证 destination 格式 "topic:tags"
      assertThat(topicCaptor.getValue()).isEqualTo(topic + ":" + opType);

      // 验证 Spring Message 的内容
      Message<?> springMsg = messageCaptor.getValue();

      // 验证 payload (应该是 String 类型的 JSON)
      assertThat(springMsg.getPayload()).isEqualTo(payloadJson);

      // 验证 headers
      assertThat(springMsg.getHeaders().get("KEYS")).isEqualTo(dedupKey); // dedupKey → KEYS
      assertThat(springMsg.getHeaders().get("channel")).isEqualTo(channel);
      assertThat(springMsg.getHeaders().get("source")).isEqualTo("scheduler");
      assertThat(springMsg.getHeaders().get("partitionKey")).isNull(); // 无 partitionKey
    }

    @Test
    @DisplayName("空 payloadJson 应使用空字符串作为消息体")
    void shouldUseEmptyStringWhenPayloadJsonIsNull() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(1L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-123")
              .payloadJson(null) // 空 payload
              .headersJson(null)
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-002", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then
      verify(rocketMQTemplate).syncSend(topicCaptor.capture(), messageCaptor.capture(), eq(3000L));

      Message<?> springMsg = messageCaptor.getValue();
      assertThat(springMsg.getPayload()).isEqualTo("{}"); // 空 payload 使用 "{}" 占位符
    }
  }

  @Nested
  @DisplayName("顺序消息发送场景 (有 partitionKey)")
  class OrderedMessageSendingTests {

    @Test
    @DisplayName("应使用 syncSendOrderly 发送顺序消息并使用 partitionKey 作为 hashKey")
    void shouldSendOrderedMessageWithPartitionKey() throws JsonProcessingException {
      // Given
      String channel = "PUBLICATION_READY";
      String topic = "INGEST_PUBLICATION_READY";
      String partitionKey = "pmid-12345";
      String dedupKey = "lit-12345";

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(2L)
              .channel(channel)
              .opType("PUBLICATION_READY")
              .dedupKey(dedupKey)
              .partitionKey(partitionKey) // 有 partitionKey
              .payloadJson("{\"pmid\":\"12345\"}")
              .headersJson("{\"retryCount\":\"0\"}")
              .build();

      when(properties.isChannelAllowed(channel)).thenReturn(true);
      when(channelMapper.toTopic(channel)).thenReturn(topic);
      when(objectMapper.readValue(anyString(), any(TypeReference.class)))
          .thenReturn(Map.of("retryCount", "0"));

      SendResult sendResult = createSuccessSendResult(topic, "msg-003", 2);
      when(rocketMQTemplate.syncSendOrderly(
              anyString(), any(Message.class), eq(partitionKey), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 验证使用 syncSendOrderly (顺序发送)
      verify(rocketMQTemplate)
          .syncSendOrderly(
              topicCaptor.capture(), messageCaptor.capture(), hashKeyCaptor.capture(), eq(3000L));
      verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), eq(3000L));

      assertThat(topicCaptor.getValue()).isEqualTo(topic + ":" + "PUBLICATION_READY");
      assertThat(hashKeyCaptor.getValue()).isEqualTo(partitionKey); // partitionKey 作为 hashKey

      // 验证 Spring Message 的消息元数据
      Message<?> springMsg = messageCaptor.getValue();
      assertThat(springMsg.getHeaders().get("KEYS"))
          .isEqualTo(dedupKey); // dedupKey → KEYS (不是 partitionKey!)
      assertThat(springMsg.getHeaders().get("partitionKey"))
          .isEqualTo(partitionKey); // partitionKey → header
    }

    @Test
    @DisplayName("空白 partitionKey 应视为普通消息")
    void shouldSendNormalMessageWhenPartitionKeyIsBlank() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(3L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-456")
              .partitionKey("   ") // 空白 partitionKey
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-004", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 应使用普通发送
      verify(rocketMQTemplate).syncSend(topicCaptor.capture(), messageCaptor.capture(), eq(3000L));
      verify(rocketMQTemplate, never())
          .syncSendOrderly(anyString(), any(Message.class), anyString(), eq(3000L));
    }
  }

  @Nested
  @DisplayName("通道白名单验证场景")
  class ChannelWhitelistTests {

    @Test
    @DisplayName("通道不在白名单中应抛出 CHANNEL_NOT_ALLOWED 异常")
    void shouldThrowExceptionWhenChannelNotAllowed() {
      // Given
      String channel = "UNKNOWN_CHANNEL";
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(4L)
              .channel(channel)
              .opType("UNKNOWN")
              .dedupKey("unk-001")
              .build();

      when(properties.isChannelAllowed(channel)).thenReturn(false); // 不在白名单中

      // When & Then
      assertThatThrownBy(() -> publisher.publish(message, createTestRelayPlan()))
          .isInstanceOf(OutboxPublishException.class)
          .hasMessageContaining("通道不在白名单中")
          .hasFieldOrPropertyWithValue("reason", Reason.CHANNEL_NOT_ALLOWED);

      // 验证没有调用 RocketMQ 发送
      verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), eq(3000L));
      verify(rocketMQTemplate, never())
          .syncSendOrderly(anyString(), any(Message.class), anyString(), eq(3000L));
    }
  }

  @Nested
  @DisplayName("发送失败场景")
  class SendFailureTests {

    @Test
    @DisplayName("SendStatus 不是 SEND_OK 应抛出 SEND_FAILED 异常")
    void shouldThrowExceptionWhenSendStatusNotOk() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(5L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-789")
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = new SendResult();
      sendResult.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT); // 发送失败
      sendResult.setMsgId("msg-error-001");
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When & Then
      assertThatThrownBy(() -> publisher.publish(message, createTestRelayPlan()))
          .isInstanceOf(OutboxPublishException.class)
          .hasMessageContaining("RocketMQ 发送失败")
          .hasMessageContaining("FLUSH_DISK_TIMEOUT")
          .hasFieldOrPropertyWithValue("reason", Reason.SEND_FAILED);
    }

    @Test
    @DisplayName("RocketMQ 抛出异常应包装为 SEND_FAILED 异常")
    void shouldWrapRocketMqExceptionAsSendFailed() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(6L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-999")
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      RuntimeException rocketMqException = new RuntimeException("连接 NameServer 失败");
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenThrow(rocketMqException);

      // When & Then
      assertThatThrownBy(() -> publisher.publish(message, createTestRelayPlan()))
          .isInstanceOf(OutboxPublishException.class)
          .hasMessageContaining("发送消息到 RocketMQ 失败")
          .hasMessageContaining("INGEST_TASK_READY")
          .hasCause(rocketMqException)
          .hasFieldOrPropertyWithValue("reason", Reason.SEND_FAILED);
    }
  }

  @Nested
  @DisplayName("Headers 解析场景")
  class HeadersParsingTests {

    @Test
    @DisplayName("空 headersJson 应返回空 Map 且不抛出异常")
    void shouldHandleNullHeadersJson() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(7L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-empty-headers")
              .payloadJson("{}")
              .headersJson(null) // 空 headers
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-005", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 成功发送,headers 为空
      verify(rocketMQTemplate).syncSend(topicCaptor.capture(), messageCaptor.capture(), eq(3000L));

      Message<?> springMsg = messageCaptor.getValue();
      // 验证 channel 属性存在
      assertThat(springMsg.getHeaders().get("channel")).isEqualTo("TASK_READY");
      // 验证没有其他自定义 headers
      assertThat(springMsg.getHeaders().get("source")).isNull();
      assertThat(springMsg.getHeaders().get("userId")).isNull();
    }

    @Test
    @DisplayName("无效 JSON 格式的 headers 应抛出 HEADERS_INVALID 异常")
    void shouldThrowExceptionWhenHeadersJsonIsInvalid() throws JsonProcessingException {
      // Given
      String invalidJson = "{invalid json}";
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(8L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-invalid-headers")
              .payloadJson("{}")
              .headersJson(invalidJson)
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      JsonProcessingException parseException =
          new JsonProcessingException("Unexpected character") {};
      when(objectMapper.readValue(eq(invalidJson), any(TypeReference.class)))
          .thenThrow(parseException);

      // When & Then
      assertThatThrownBy(() -> publisher.publish(message, createTestRelayPlan()))
          .isInstanceOf(OutboxPublishException.class)
          .hasMessageContaining("解析 Outbox headers JSON 失败")
          .hasCause(parseException)
          .hasFieldOrPropertyWithValue("reason", Reason.HEADERS_INVALID);
    }

    @Test
    @DisplayName("多个 headers 应正确设置为 UserProperties")
    void shouldSetMultipleHeadersAsUserProperties() throws JsonProcessingException {
      // Given
      String headersJson =
          "{\"traceId\":\"trace-001\",\"spanId\":\"span-001\",\"priority\":\"HIGH\"}";
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(9L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-multi-headers")
              .payloadJson("{}")
              .headersJson(headersJson)
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      Map<String, Object> headers =
          Map.of("traceId", "trace-001", "spanId", "span-001", "priority", "HIGH");
      when(objectMapper.readValue(eq(headersJson), any(TypeReference.class))).thenReturn(headers);

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-006", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then
      verify(rocketMQTemplate).syncSend(topicCaptor.capture(), messageCaptor.capture(), eq(3000L));

      Message<?> springMsg = messageCaptor.getValue();
      assertThat(springMsg.getHeaders().get("traceId")).isEqualTo("trace-001");
      assertThat(springMsg.getHeaders().get("spanId")).isEqualTo("span-001");
      assertThat(springMsg.getHeaders().get("priority")).isEqualTo("HIGH");
      assertThat(springMsg.getHeaders().get("channel")).isEqualTo("TASK_READY");
    }
  }

  @Nested
  @DisplayName("消息元数据映射场景")
  class MessageMetadataMappingTests {

    @Test
    @DisplayName("dedupKey 应映射到 RocketMQ KEYS,partitionKey 应映射到 UserProperty")
    void shouldCorrectlyMapDedupKeyAndPartitionKey() throws JsonProcessingException {
      // Given: dedupKey 和 partitionKey 不同 (这是架构修正的关键点!)
      String dedupKey = "dedup-key-123"; // 用于去重和追踪
      String partitionKey = "partition-key-456"; // 用于队列选择

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(10L)
              .channel("PUBLICATION_READY")
              .opType("PUBLICATION_READY")
              .dedupKey(dedupKey)
              .partitionKey(partitionKey)
              .payloadJson("{}")
              .headersJson("{}")
              .build();

      when(properties.isChannelAllowed("PUBLICATION_READY")).thenReturn(true);
      when(channelMapper.toTopic("PUBLICATION_READY")).thenReturn("INGEST_PUBLICATION_READY");
      when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of());

      SendResult sendResult = createSuccessSendResult("INGEST_PUBLICATION_READY", "msg-007", 1);
      when(rocketMQTemplate.syncSendOrderly(
              anyString(), any(Message.class), anyString(), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then
      verify(rocketMQTemplate)
          .syncSendOrderly(
              topicCaptor.capture(), messageCaptor.capture(), hashKeyCaptor.capture(), eq(3000L));

      Message<?> springMsg = messageCaptor.getValue();

      // 关键验证: dedupKey → KEYS, partitionKey → hashKey + header
      assertThat(springMsg.getHeaders().get("KEYS")).isEqualTo(dedupKey); // KEYS 用于去重和追踪
      assertThat(hashKeyCaptor.getValue()).isEqualTo(partitionKey); // hashKey 用于队列选择
      assertThat(springMsg.getHeaders().get("partitionKey")).isEqualTo(partitionKey); // 供消费端使用
    }

    @Test
    @DisplayName("opType 应映射到 RocketMQ TAGS")
    void shouldMapOpTypeToTags() {
      // Given
      String opType = "TASK_COMPLETED";
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(11L)
              .channel("TASK_READY")
              .opType(opType)
              .dedupKey("task-completed-001")
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-008", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then
      verify(rocketMQTemplate).syncSend(topicCaptor.capture(), messageCaptor.capture(), eq(3000L));

      // 验证 destination 包含 tags (格式: "topic:tags")
      assertThat(topicCaptor.getValue())
          .isEqualTo("INGEST_TASK_READY:" + opType); // opType → destination 中的 tags
    }
  }

  // ==================== 辅助方法 ====================

  /**
   * 创建测试用的 OutboxMessage Builder,预填充必填字段。
   *
   * @return OutboxMessage.Builder
   */
  private OutboxMessage.Builder createBaseOutboxMessageBuilder() {
    return OutboxMessage.builder()
        .aggregateType("Task") // 必填字段
        .aggregateId(999L) // 必填字段
        .partitionKey(""); // 默认空字符串,避免 null 异常
  }

  /**
   * 创建测试用的 RelayPlan。
   *
   * @return RelayPlan 实例
   */
  private RelayPlan createTestRelayPlan() {
    return new RelayPlan(
        null, // channel (null 表示所有通道)
        Instant.now(),
        100, // batchSize
        Duration.ofMinutes(5), // leaseDuration
        3, // maxAttempts
        Duration.ofSeconds(1), // initialBackoff
        2.0, // backoffMultiplier
        Duration.ofMinutes(10), // maxBackoff
        "test-owner" // leaseOwner
        );
  }

  /**
   * 创建成功的 SendResult。
   *
   * @param topic Topic 名称
   * @param msgId 消息 ID
   * @param queueId 队列 ID
   * @return SendResult
   */
  private SendResult createSuccessSendResult(String topic, String msgId, int queueId) {
    SendResult result = new SendResult();
    result.setSendStatus(SendStatus.SEND_OK);
    result.setMsgId(msgId);

    MessageQueue messageQueue = new MessageQueue(topic, "broker-a", queueId);
    result.setMessageQueue(messageQueue);

    return result;
  }
}
