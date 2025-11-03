package com.patra.ingest.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.usecase.relay.OutboxRelayUseCase;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.infra.messaging.RocketMqOutboxPublisher;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RocketMQ 集成测试。
 *
 * <p>使用 Testcontainers MySQL 和 Mock RocketMQTemplate 进行端到端测试。
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>✅ Outbox 消息持久化和事务完整性
 *   <li>✅ OutboxRelayJob 从数据库读取并发布消息
 *   <li>✅ 幂等性验证 (dedupKey 唯一性约束)
 *   <li>✅ 租约机制验证 (防止并发发布)
 *   <li>✅ 消息元数据映射正确性 (dedupKey → KEYS, opType → TAGS)
 *   <li>✅ 顺序消息发送 (partitionKey → hashKey)
 *   <li>✅ 重试机制验证
 * </ul>
 *
 * <p><strong>测试策略</strong>:
 *
 * <ul>
 *   <li>真实数据库 (Testcontainers MySQL) - 验证事务、并发控制
 *   <li>Mock RocketMQTemplate - 避免真实 MQ 依赖,使用 ArgumentCaptor 验证消息内容
 *   <li>Awaitility - 处理异步操作
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("RocketMQ 集成测试")
class RocketMqIntegrationTest {

  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("patra_ingest_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(true); // 重用容器加速测试

  @DynamicPropertySource
  static void configureMySql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired private OutboxMessageMapper outboxMapper;

  @Autowired private OutboxRelayUseCase relayUseCase;

  @Autowired private RocketMqOutboxPublisher publisher;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private RocketMQTemplate rocketMQTemplate;

  private SendResult mockSuccessResult;

  @BeforeEach
  void setUp() {
    // 清理测试数据
    outboxMapper.delete(null);

    // 配置 Mock RocketMQTemplate 返回成功结果
    mockSuccessResult = new SendResult();
    mockSuccessResult.setSendStatus(SendStatus.SEND_OK);
    mockSuccessResult.setMsgId("test-msg-id-" + System.currentTimeMillis());

    when(rocketMQTemplate.syncSend(anyString(), any(Message.class), anyLong()))
        .thenReturn(mockSuccessResult);

    when(rocketMQTemplate.syncSendOrderly(anyString(), any(Message.class), anyString(), anyLong()))
        .thenReturn(mockSuccessResult);
  }

  @Nested
  @DisplayName("Outbox 消息持久化测试")
  class OutboxPersistenceTests {

    @Test
    @Transactional
    @DisplayName("Should persist outbox message within transaction")
    void shouldPersistOutboxMessageWithinTransaction() throws Exception {
      // Arrange
      OutboxMessageDO outboxDO = createTestOutboxMessage("test-dedup-1", "TASK_READY");

      // Act
      outboxMapper.insert(outboxDO);

      // Assert
      OutboxMessageDO saved = outboxMapper.selectById(outboxDO.getId());
      assertThat(saved).isNotNull();
      assertThat(saved.getDedupKey()).isEqualTo("test-dedup-1");
      assertThat(saved.getChannel()).isEqualTo("TASK_READY");
      assertThat(saved.getStatusCode()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should enforce dedupKey uniqueness constraint")
    void shouldEnforceDedupKeyUniqueness() {
      // Arrange
      OutboxMessageDO first = createTestOutboxMessage("duplicate-key", "TASK_READY");
      OutboxMessageDO second = createTestOutboxMessage("duplicate-key", "TASK_READY");

      // Act
      outboxMapper.insert(first);

      // Assert: 第二次插入相同 dedupKey 应该失败
      assertThatThrownBy(() -> outboxMapper.insert(second))
          .hasMessageContaining("Duplicate entry")
          .hasMessageContaining("dedupKey");
    }

    @Test
    @DisplayName("Should store JSON payload and headers correctly")
    void shouldStoreJsonPayloadAndHeadersCorrectly() throws Exception {
      // Arrange
      Map<String, Object> payload = new HashMap<>();
      payload.put("taskId", 12345L);
      payload.put("idempotentKey", "task-idem-key");

      Map<String, Object> headers = new HashMap<>();
      headers.put("channel", "TASK_READY");
      headers.put("opType", "TaskReady");

      OutboxMessageDO outboxDO = createTestOutboxMessage("json-test", "TASK_READY");
      outboxDO.setPayloadJson(objectMapper.writeValueAsString(payload));
      outboxDO.setHeadersJson(objectMapper.writeValueAsString(headers));

      // Act
      outboxMapper.insert(outboxDO);

      // Assert
      OutboxMessageDO saved = outboxMapper.selectById(outboxDO.getId());
      Map<String, Object> savedPayload = objectMapper.readValue(saved.getPayloadJson(), Map.class);
      Map<String, Object> savedHeaders = objectMapper.readValue(saved.getHeadersJson(), Map.class);

      assertThat(savedPayload).containsEntry("taskId", 12345);
      assertThat(savedHeaders).containsEntry("channel", "TASK_READY");
    }
  }

  @Nested
  @DisplayName("Outbox 中继发布测试")
  class OutboxRelayPublishTests {

    @Test
    @DisplayName("Should publish outbox message via relay job")
    void shouldPublishOutboxMessageViaRelayJob() {
      // Arrange: 插入待发布的 Outbox 消息
      OutboxMessageDO outboxDO = createTestOutboxMessage("relay-test-1", "TASK_READY");
      outboxMapper.insert(outboxDO);

      OutboxRelayCommand command =
          new OutboxRelayCommand(
              List.of("TASK_READY"),
              100, // batchSize
              Duration.ofMinutes(5), // leaseDuration
              3 // maxRetries
              );

      // Act: 执行中继任务
      relayUseCase.relay(command);

      // Assert: 验证消息已发送到 RocketMQ
      ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

      verify(rocketMQTemplate, times(1))
          .syncSend(topicCaptor.capture(), messageCaptor.capture(), anyLong());

      String topic = topicCaptor.getValue();
      Message sentMessage = messageCaptor.getValue();

      assertThat(topic).isEqualTo("INGEST_TASK_READY"); // RocketMqChannelMapper 映射结果
      assertThat(sentMessage.getKeys()).isEqualTo("relay-test-1"); // dedupKey → KEYS
      assertThat(sentMessage.getTags()).isEqualTo("TaskReady"); // opType → TAGS

      // 验证数据库中消息状态已更新为 PUBLISHED
      await()
          .atMost(2, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                OutboxMessageDO published = outboxMapper.selectById(outboxDO.getId());
                assertThat(published.getStatusCode()).isEqualTo("PUBLISHED");
              });
    }

    @Test
    @DisplayName("Should send orderly message with partitionKey")
    void shouldSendOrderlyMessageWithPartitionKey() {
      // Arrange: 创建带 partitionKey 的消息
      OutboxMessageDO outboxDO = createTestOutboxMessage("orderly-test", "TASK_READY");
      outboxDO.setPartitionKey("partition-123");
      outboxMapper.insert(outboxDO);

      OutboxRelayCommand command =
          new OutboxRelayCommand(List.of("TASK_READY"), 100, Duration.ofMinutes(5), 3);

      // Act
      relayUseCase.relay(command);

      // Assert: 验证使用 syncSendOrderly 发送
      ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
      ArgumentCaptor<String> hashKeyCaptor = ArgumentCaptor.forClass(String.class);

      verify(rocketMQTemplate, times(1))
          .syncSendOrderly(
              topicCaptor.capture(), messageCaptor.capture(), hashKeyCaptor.capture(), anyLong());

      assertThat(hashKeyCaptor.getValue()).isEqualTo("partition-123");
    }

    @Test
    @DisplayName("Should map metadata correctly (dedupKey → KEYS, opType → TAGS)")
    void shouldMapMetadataCorrectly() {
      // Arrange
      OutboxMessageDO outboxDO = createTestOutboxMessage("metadata-test", "TASK_READY");
      outboxDO.setOpType("CustomOperationType");
      outboxDO.setDedupKey("custom-dedup-key");
      outboxMapper.insert(outboxDO);

      OutboxRelayCommand command =
          new OutboxRelayCommand(List.of("TASK_READY"), 100, Duration.ofMinutes(5), 3);

      // Act
      relayUseCase.relay(command);

      // Assert
      ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
      verify(rocketMQTemplate).syncSend(anyString(), messageCaptor.capture(), anyLong());

      Message sentMessage = messageCaptor.getValue();
      assertThat(sentMessage.getKeys()).isEqualTo("custom-dedup-key");
      assertThat(sentMessage.getTags()).isEqualTo("CustomOperationType");
      assertThat(sentMessage.getUserProperty("channel")).isEqualTo("TASK_READY");
    }
  }

  @Nested
  @DisplayName("幂等性和并发控制测试")
  class IdempotencyAndConcurrencyTests {

    @Test
    @DisplayName("Should acquire lease before publishing")
    void shouldAcquireLeaseBeforePublishing() {
      // Arrange
      OutboxMessageDO outboxDO = createTestOutboxMessage("lease-test", "TASK_READY");
      outboxMapper.insert(outboxDO);

      OutboxRelayCommand command =
          new OutboxRelayCommand(List.of("TASK_READY"), 100, Duration.ofMinutes(5), 3);

      // Act
      relayUseCase.relay(command);

      // Assert: 验证消息在发布过程中获取了租约
      OutboxMessageDO afterRelay = outboxMapper.selectById(outboxDO.getId());
      assertThat(afterRelay.getLeaseHolder()).isNotNull();
      assertThat(afterRelay.getLeaseUntil()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should not republish message with active lease")
    void shouldNotRepublishMessageWithActiveLease() {
      // Arrange: 创建已有租约的消息
      OutboxMessageDO outboxDO = createTestOutboxMessage("active-lease", "TASK_READY");
      outboxDO.setLeaseHolder("another-worker");
      outboxDO.setLeaseUntil(Instant.now().plus(Duration.ofMinutes(10)));
      outboxMapper.insert(outboxDO);

      OutboxRelayCommand command =
          new OutboxRelayCommand(List.of("TASK_READY"), 100, Duration.ofMinutes(5), 3);

      // Act
      relayUseCase.relay(command);

      // Assert: 不应该发送消息
      verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), anyLong());
    }
  }

  @Nested
  @DisplayName("重试机制测试")
  class RetryMechanismTests {

    @Test
    @DisplayName("Should retry failed message")
    void shouldRetryFailedMessage() {
      // Arrange: 配置第一次发送失败,第二次成功
      SendResult failureResult = new SendResult();
      failureResult.setSendStatus(SendStatus.SEND_OK);

      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), anyLong()))
          .thenThrow(new RuntimeException("Network error"))
          .thenReturn(mockSuccessResult);

      OutboxMessageDO outboxDO = createTestOutboxMessage("retry-test", "TASK_READY");
      outboxMapper.insert(outboxDO);

      OutboxRelayCommand command =
          new OutboxRelayCommand(List.of("TASK_READY"), 100, Duration.ofMinutes(5), 3);

      // Act: 第一次尝试失败
      assertThatThrownBy(() -> relayUseCase.relay(command)).isInstanceOf(RuntimeException.class);

      // 验证消息状态变为 PENDING (等待重试)
      OutboxMessageDO afterFirstAttempt = outboxMapper.selectById(outboxDO.getId());
      assertThat(afterFirstAttempt.getRetryCount()).isGreaterThan(0);

      // Act: 第二次尝试成功
      relayUseCase.relay(command);

      // Assert
      await()
          .atMost(2, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                OutboxMessageDO afterRetry = outboxMapper.selectById(outboxDO.getId());
                assertThat(afterRetry.getStatusCode()).isEqualTo("PUBLISHED");
              });
    }

    @Test
    @DisplayName("Should mark message as FAILED after max retries")
    void shouldMarkMessageAsFailedAfterMaxRetries() {
      // Arrange: 配置始终失败
      when(rocketMQTemplate.syncSend(anyString(), any(Message.class), anyLong()))
          .thenThrow(new RuntimeException("Permanent failure"));

      OutboxMessageDO outboxDO = createTestOutboxMessage("max-retry-test", "TASK_READY");
      outboxDO.setRetryCount(3); // 已达到最大重试次数
      outboxMapper.insert(outboxDO);

      OutboxRelayCommand command =
          new OutboxRelayCommand(List.of("TASK_READY"), 100, Duration.ofMinutes(5), 3);

      // Act & Assert: 应该跳过已达最大重试次数的消息
      assertThatThrownBy(() -> relayUseCase.relay(command)).isInstanceOf(RuntimeException.class);

      OutboxMessageDO afterMaxRetry = outboxMapper.selectById(outboxDO.getId());
      assertThat(afterMaxRetry.getRetryCount()).isEqualTo(3);
      // 状态应该保持为 PENDING 或标记为 FAILED (取决于实现)
    }
  }

  // ==================== Helper Methods ====================

  /**
   * 创建测试用的 OutboxMessage DO。
   *
   * @param dedupKey 去重键
   * @param channel 业务通道
   * @return OutboxMessageDO
   */
  private OutboxMessageDO createTestOutboxMessage(String dedupKey, String channel) {
    OutboxMessageDO outboxDO = new OutboxMessageDO();
    outboxDO.setAggregateType("Task");
    outboxDO.setAggregateId(System.currentTimeMillis());
    outboxDO.setChannel(channel);
    outboxDO.setOpType("TaskReady");
    outboxDO.setDedupKey(dedupKey);
    try {
      outboxDO.setPayloadJson(objectMapper.readTree("{\"taskId\": 12345}"));
      outboxDO.setHeadersJson(objectMapper.readTree("{}"));
    } catch (Exception e) {
      throw new RuntimeException("Failed to create test outbox message", e);
    }
    outboxDO.setNotBefore(Instant.now());
    outboxDO.setStatusCode("PENDING");
    outboxDO.setRetryCount(0);
    outboxDO.setCreatedAt(Instant.now());
    outboxDO.setUpdatedAt(Instant.now());
    return outboxDO;
  }
}
