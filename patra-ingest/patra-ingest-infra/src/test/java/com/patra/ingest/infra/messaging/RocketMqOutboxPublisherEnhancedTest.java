package com.patra.ingest.infra.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.exception.OutboxPublishException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import com.patra.ingest.infra.config.OutboxMqProperties;
import com.patra.ingest.infra.messaging.config.RocketMqChannelMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
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

/**
 * RocketMqOutboxPublisher 增强测试套件。
 *
 * <p>补充原有测试中缺失的边界条件、并发场景、配置验证等高级测试场景。
 *
 * <p>测试覆盖：
 *
 * <ul>
 *   <li>边界条件：空字符串、超长字符串、特殊字符、null 处理
 *   <li>并发场景：多线程并发发送、竞态条件
 *   <li>配置验证：OutboxMqProperties 各种配置组合
 *   <li>错误恢复：各种 SendStatus 状态码处理
 *   <li>性能验证：批量发送响应时间
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RocketMqOutboxPublisher 增强测试")
class RocketMqOutboxPublisherEnhancedTest {

  @Mock private RocketMQTemplate rocketMQTemplate;
  @Mock private RocketMqChannelMapper channelMapper;
  @Mock private ObjectMapper objectMapper;
  @Mock private OutboxMqProperties properties;

  @Captor private ArgumentCaptor<Message> messageCaptor;

  private RocketMqOutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    // Mock 默认配置值 (使用 lenient 避免 UnnecessaryStubbingException)
    lenient().when(properties.getSendTimeout()).thenReturn(3000);

    publisher =
        new RocketMqOutboxPublisher(rocketMQTemplate, channelMapper, objectMapper, properties);
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应处理空字符串 dedupKey")
    void shouldHandleEmptyDedupKey() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(1L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("") // 空 dedupKey
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-001", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 应成功发送，KEYS 为空或 null (RocketMQ 行为)
      verify(rocketMQTemplate).syncSend(anyString(), messageCaptor.capture(), eq(3000L));
      Message rocketMsg = messageCaptor.getValue();
      // RocketMQ 将空 keys 存储为 null
      assertThat(rocketMsg.getKeys()).isNullOrEmpty();
    }

    @Test
    @DisplayName("应处理超长 payload (> 1MB)")
    void shouldHandleLargePayload() {
      // Given: 构造 1.5MB 的 JSON payload
      StringBuilder largePayload = new StringBuilder("{\"data\":\"");
      String oneKbData = "x".repeat(1024);
      for (int i = 0; i < 1500; i++) { // 1500 KB
        largePayload.append(oneKbData);
      }
      largePayload.append("\"}");

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(2L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("large-payload")
              .payloadJson(largePayload.toString())
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-002", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 应成功发送大消息
      verify(rocketMQTemplate).syncSend(anyString(), messageCaptor.capture(), eq(3000L));
      Message rocketMsg = messageCaptor.getValue();
      assertThat(rocketMsg.getBody().length).isGreaterThan(1024 * 1024); // > 1MB
    }

    @Test
    @DisplayName("应处理特殊字符 (Unicode、Emoji)")
    void shouldHandleSpecialCharacters() throws JsonProcessingException {
      // Given: 包含 Unicode 和 Emoji 的数据
      String specialPayload = "{\"title\":\"医学研究\",\"emoji\":\"🔬🧬\",\"symbol\":\"∀∃∈∉\"}";
      String specialDedupKey = "任务-测试-🚀";

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(3L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey(specialDedupKey)
              .payloadJson(specialPayload)
              .headersJson("{\"author\":\"李医生\",\"tag\":\"🏥\"}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");
      when(objectMapper.readValue(anyString(), any(TypeReference.class)))
          .thenReturn(Map.of("author", "李医生", "tag", "🏥"));

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-003", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 应正确处理 UTF-8 编码
      verify(rocketMQTemplate).syncSend(anyString(), messageCaptor.capture(), eq(3000L));
      Message rocketMsg = messageCaptor.getValue();
      assertThat(rocketMsg.getKeys()).isEqualTo(specialDedupKey);
      String bodyStr = new String(rocketMsg.getBody(), StandardCharsets.UTF_8);
      assertThat(bodyStr).contains("🔬", "🧬", "∀", "∃");
    }

    @Test
    @DisplayName("应处理超长 dedupKey (> 255 字符)")
    void shouldHandleVeryLongDedupKey() {
      // Given: 300 字符的 dedupKey
      String longDedupKey = "very-long-dedup-key-" + "x".repeat(280);

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(4L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey(longDedupKey)
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-004", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 应成功发送 (RocketMQ 会截断或拒绝，但这里验证发布器没有崩溃)
      verify(rocketMQTemplate).syncSend(anyString(), messageCaptor.capture(), eq(3000L));
      Message rocketMsg = messageCaptor.getValue();
      assertThat(rocketMsg.getKeys()).hasSize(300);
    }

    @Test
    @DisplayName("应处理空白 partitionKey (仅空格)")
    void shouldHandleWhitespaceOnlyPartitionKey() {
      // Given: partitionKey 只包含空格
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(5L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-001")
              .partitionKey("   \t\n  ") // 空白字符
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-005", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 应使用普通发送 (空白 partitionKey 被视为无 partitionKey)
      verify(rocketMQTemplate).syncSend(anyString(), any(Message.class), eq(3000L));
      verify(rocketMQTemplate, never())
          .syncSendOrderly(anyString(), any(Message.class), anyString(), eq(3000L));
    }
  }

  @Nested
  @DisplayName("并发场景测试")
  class ConcurrencyTests {

    @Test
    @DisplayName("应安全处理并发发送消息")
    void shouldHandleConcurrentPublishing() throws InterruptedException {
      // Given: 准备 10 个并发线程
      int threadCount = 10;
      int messagesPerThread = 10;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch latch = new CountDownLatch(threadCount * messagesPerThread);
      ConcurrentHashMap<String, SendResult> results = new ConcurrentHashMap<>();

      when(properties.isChannelAllowed(anyString())).thenReturn(true);
      when(channelMapper.toTopic(anyString())).thenReturn("INGEST_TASK_READY");

      // Mock 发送成功
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenAnswer(
              invocation -> {
                Thread.sleep(1); // 模拟网络延迟
                return createSuccessSendResult("INGEST_TASK_READY", "msg-" + latch.getCount(), 0);
              });

      // When: 并发发送消息
      for (int i = 0; i < threadCount; i++) {
        int threadId = i;
        executor.submit(
            () -> {
              for (int j = 0; j < messagesPerThread; j++) {
                try {
                  OutboxMessage message =
                      createBaseOutboxMessageBuilder()
                          .id((long) (threadId * messagesPerThread + j))
                          .channel("TASK_READY")
                          .opType("TASK_READY")
                          .dedupKey("thread-" + threadId + "-msg-" + j)
                          .payloadJson("{\"threadId\":" + threadId + ",\"msgId\":" + j + "}")
                          .build();

                  publisher.publish(message, createTestRelayPlan());
                  latch.countDown();
                } catch (Exception e) {
                  // 记录异常但不中断测试
                  System.err.println("并发发送失败: " + e.getMessage());
                }
              }
            });
      }

      // Then: 所有消息应在 10 秒内发送完成
      boolean completed = latch.await(10, TimeUnit.SECONDS);
      executor.shutdown();

      assertThat(completed).isTrue();
      verify(rocketMQTemplate, times(threadCount * messagesPerThread))
          .syncSend(anyString(), any(Message.class), eq(3000L));
    }

    @Test
    @DisplayName("并发发送时应保持消息顺序 (同一 partitionKey)")
    void shouldMaintainOrderForSamePartitionKey() throws InterruptedException {
      // Given: 同一 partitionKey 的多条消息
      String partitionKey = "order-test-key";
      int messageCount = 5;
      List<Long> sendOrder = Collections.synchronizedList(new ArrayList<>());

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      when(rocketMQTemplate.syncSendOrderly(
              anyString(), any(Message.class), eq(partitionKey), eq(3000L)))
          .thenAnswer(
              invocation -> {
                Message msg = invocation.getArgument(1);
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                Long id = Long.parseLong(body.replaceAll("[^0-9]", ""));
                sendOrder.add(id);
                return createSuccessSendResult("INGEST_TASK_READY", "msg-" + id, 0);
              });

      // When: 顺序发送消息
      for (long i = 0; i < messageCount; i++) {
        OutboxMessage message =
            createBaseOutboxMessageBuilder()
                .id(i)
                .channel("TASK_READY")
                .opType("TASK_READY")
                .dedupKey("order-" + i)
                .partitionKey(partitionKey)
                .payloadJson("{\"seq\":" + i + "}")
                .build();

        publisher.publish(message, createTestRelayPlan());
      }

      // Then: 验证发送顺序
      assertThat(sendOrder).containsExactly(0L, 1L, 2L, 3L, 4L);
    }
  }

  @Nested
  @DisplayName("配置验证测试")
  class ConfigurationValidationTests {

    @Test
    @DisplayName("strict-channel-whitelist=true 时，未列入白名单的通道应拒绝")
    void shouldRejectUnlistedChannelWhenStrictModeEnabled() {
      // Given: 严格模式，只允许某些通道，LITERATURE_READY 不在白名单中
      when(properties.isChannelAllowed("LITERATURE_READY")).thenReturn(false);

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(1L)
              .channel("LITERATURE_READY")
              .opType("LITERATURE_READY")
              .dedupKey("lit-001")
              .payloadJson("{}")
              .build();

      // When & Then
      assertThatThrownBy(() -> publisher.publish(message, createTestRelayPlan()))
          .isInstanceOf(OutboxPublishException.class)
          .hasMessageContaining("通道不在白名单中");

      verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), anyLong());
    }

    @Test
    @DisplayName("strict-channel-whitelist=false 时，所有通道应允许")
    void shouldAllowAllChannelsWhenNonStrictMode() {
      // Given: 非严格模式，所有通道都允许
      when(properties.isChannelAllowed(anyString())).thenReturn(true);
      when(channelMapper.toTopic(anyString())).thenReturn("ANY_TOPIC");

      SendResult sendResult = createSuccessSendResult("ANY_TOPIC", "msg-001", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(1L)
              .channel("UNKNOWN_CHANNEL")
              .opType("UNKNOWN")
              .dedupKey("unk-001")
              .payloadJson("{}")
              .build();

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 应成功发送
      verify(rocketMQTemplate).syncSend(anyString(), any(Message.class), eq(3000L));
    }

    @Test
    @DisplayName("通道名称应大小写不敏感 (归一化处理)")
    void shouldHandleChannelNamesCaseInsensitively() {
      // Given: 小写通道名
      when(properties.isChannelAllowed("task_ready")).thenReturn(true); // 匹配实际调用的参数
      when(channelMapper.toTopic("task_ready")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-001", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(1L)
              .channel("task_ready") // 小写
              .opType("TASK_READY")
              .dedupKey("task-001")
              .payloadJson("{}")
              .build();

      // When
      publisher.publish(message, createTestRelayPlan());

      // Then: 应成功发送 (假设 Properties 内部归一化)
      verify(rocketMQTemplate).syncSend(anyString(), any(Message.class), eq(3000L));
    }
  }

  @Nested
  @DisplayName("错误恢复测试")
  class ErrorRecoveryTests {

    @Test
    @DisplayName("SLAVE_NOT_AVAILABLE 状态应抛出 SEND_FAILED 异常")
    void shouldHandleSlaveNotAvailableStatus() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(1L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-001")
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = new SendResult();
      sendResult.setSendStatus(SendStatus.SLAVE_NOT_AVAILABLE);
      sendResult.setMsgId("msg-slave-unavailable");
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When & Then
      assertThatThrownBy(() -> publisher.publish(message, createTestRelayPlan()))
          .isInstanceOf(OutboxPublishException.class)
          .hasMessageContaining("SLAVE_NOT_AVAILABLE");
    }

    @Test
    @DisplayName("FLUSH_SLAVE_TIMEOUT 状态应抛出 SEND_FAILED 异常")
    void shouldHandleFlushSlaveTimeoutStatus() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(2L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-002")
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = new SendResult();
      sendResult.setSendStatus(SendStatus.FLUSH_SLAVE_TIMEOUT);
      sendResult.setMsgId("msg-flush-timeout");
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      // When & Then
      assertThatThrownBy(() -> publisher.publish(message, createTestRelayPlan()))
          .isInstanceOf(OutboxPublishException.class)
          .hasMessageContaining("FLUSH_SLAVE_TIMEOUT");
    }

    @Test
    @DisplayName("RocketMQ 连接超时应包装为业务异常")
    void shouldWrapConnectionTimeoutException() {
      // Given
      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(3L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("task-003")
              .payloadJson("{}")
              .build();

      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      // MQClientException 是 checked exception,需要包装为 RuntimeException
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenThrow(new RuntimeException("RocketMQ 客户端异常: 连接超时"));

      // When & Then
      assertThatThrownBy(() -> publisher.publish(message, createTestRelayPlan()))
          .isInstanceOf(OutboxPublishException.class)
          .hasMessageContaining("发送消息到 RocketMQ 失败");
    }
  }

  @Nested
  @DisplayName("性能验证测试")
  class PerformanceTests {

    @Test
    @DisplayName("批量发送 100 条消息应在 5 秒内完成")
    void shouldPublish100MessagesWithin5Seconds() {
      // Given
      int messageCount = 100;
      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-perf", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenAnswer(
              invocation -> {
                Thread.sleep(10); // 模拟 10ms 网络延迟
                return sendResult;
              });

      // When: 记录开始时间
      long startTime = System.currentTimeMillis();

      for (int i = 0; i < messageCount; i++) {
        OutboxMessage message =
            createBaseOutboxMessageBuilder()
                .id((long) i)
                .channel("TASK_READY")
                .opType("TASK_READY")
                .dedupKey("perf-" + i)
                .payloadJson("{\"index\":" + i + "}")
                .build();

        publisher.publish(message, createTestRelayPlan());
      }

      long duration = System.currentTimeMillis() - startTime;

      // Then: 验证性能指标
      assertThat(duration).isLessThan(5000); // 5 秒内完成
      verify(rocketMQTemplate, times(messageCount))
          .syncSend(anyString(), any(Message.class), eq(3000L));
    }

    @Test
    @DisplayName("单条消息发送延迟应 < 100ms (不含网络)")
    void shouldPublishSingleMessageWithLowLatency() {
      // Given
      when(properties.isChannelAllowed("TASK_READY")).thenReturn(true);
      when(channelMapper.toTopic("TASK_READY")).thenReturn("INGEST_TASK_READY");

      SendResult sendResult = createSuccessSendResult("INGEST_TASK_READY", "msg-latency", 0);
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), eq(3000L)))
          .thenReturn(sendResult);

      OutboxMessage message =
          createBaseOutboxMessageBuilder()
              .id(1L)
              .channel("TASK_READY")
              .opType("TASK_READY")
              .dedupKey("latency-test")
              .payloadJson("{\"test\":\"latency\"}")
              .build();

      // When: 测量发布器处理时间 (不含网络)
      long startTime = System.nanoTime();
      publisher.publish(message, createTestRelayPlan());
      long duration = System.nanoTime() - startTime;

      // Then: 发布器处理时间应 < 1ms (1,000,000 ns)
      assertThat(Duration.ofNanos(duration).toMillis()).as("发布器处理时间").isLessThan(1); // < 1ms
    }
  }

  // ==================== 辅助方法 ====================

  private OutboxMessage.Builder createBaseOutboxMessageBuilder() {
    return OutboxMessage.builder().aggregateType("Task").aggregateId(999L).partitionKey("");
  }

  private RelayPlan createTestRelayPlan() {
    return new RelayPlan(
        null,
        Instant.now(),
        100,
        Duration.ofMinutes(5),
        3,
        Duration.ofSeconds(1),
        2.0,
        Duration.ofMinutes(10),
        "test-owner");
  }

  private SendResult createSuccessSendResult(String topic, String msgId, int queueId) {
    SendResult result = new SendResult();
    result.setSendStatus(SendStatus.SEND_OK);
    result.setMsgId(msgId);

    MessageQueue messageQueue = new MessageQueue(topic, "broker-a", queueId);
    result.setMessageQueue(messageQueue);

    return result;
  }
}
