package com.patra.ingest.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.config.IngestMySQLContainerInitializer;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// OutboxMessageRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + MySQL 8 测试发件箱消息持久化。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 MySQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：saveAll、saveOrUpdate、findByChannelAndDedup、fetchPending、租约操作、状态转换
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = IngestMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OutboxMessageRepositoryAdapter.class,
  TestMybatisPlusAutoConfiguration.class,
  JacksonAutoConfiguration.class
})
@ComponentScan("com.patra.ingest.infra.persistence.converter")
@MapperScan("com.patra.ingest.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("OutboxMessageRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OutboxMessageRepositoryAdapterIT {

  @Autowired private OutboxMessageRepositoryAdapter repository;

  @Autowired private OutboxMessageMapper outboxMessageMapper;
  @Autowired private ObjectMapper objectMapper;

  private static final String TEST_CHANNEL = "ingest.task";
  private static final String TEST_OWNER = "relay-01";
  private static final Instant TEST_NOW = Instant.parse("2025-01-01T00:00:00Z");

  @BeforeEach
  void setUp() {
    // 清理现有数据
    outboxMessageMapper.delete(
        Wrappers.<OutboxMessageDO>lambdaQuery().ne(OutboxMessageDO::getId, 0L));
  }

  @Nested
  @DisplayName("查询操作")
  class FindTests {

    @Test
    @DisplayName("应按通道和去重键查找消息")
    void shouldFindByChannelAndDedup() {
      // Given
      OutboxMessageDO messageDO = createTestOutboxMessageDO("dedup-001");
      outboxMessageMapper.insert(messageDO);

      // When
      Optional<OutboxMessage> result = repository.findByChannelAndDedup(TEST_CHANNEL, "dedup-001");

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getChannel()).isEqualTo(TEST_CHANNEL);
      assertThat(result.get().getDedupKey()).isEqualTo("dedup-001");
    }

    @Test
    @DisplayName("应在消息不存在时返回空 Optional")
    void shouldReturnEmptyWhenMessageNotFound() {
      // Given: 不插入任何消息

      // When
      Optional<OutboxMessage> result =
          repository.findByChannelAndDedup(TEST_CHANNEL, "non-existent");

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("获取待发布消息")
  class FetchPendingTests {

    @Test
    @DisplayName("应获取指定通道的待发布消息")
    void shouldFetchPendingMessagesForChannel() {
      // Given
      OutboxMessageDO message1 = createTestOutboxMessageDO("dedup-001");
      OutboxMessageDO message2 = createTestOutboxMessageDO("dedup-002");
      outboxMessageMapper.insert(message1);
      outboxMessageMapper.insert(message2);

      // When
      List<OutboxMessage> result = repository.fetchPending(TEST_CHANNEL, TEST_NOW, 10);

      // Then
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("应在 limit 小于等于 0 时返回空列表")
    void shouldReturnEmptyListWhenLimitIsZeroOrNegative() {
      // When
      List<OutboxMessage> result = repository.fetchPending(TEST_CHANNEL, TEST_NOW, 0);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应支持从所有通道获取消息")
    void shouldFetchPendingFromAllChannelsWhenChannelIsNull() {
      // Given
      OutboxMessageDO messageDO = createTestOutboxMessageDO("dedup-001");
      outboxMessageMapper.insert(messageDO);

      // When
      List<OutboxMessage> result = repository.fetchPending(null, TEST_NOW, 10);

      // Then
      assertThat(result).hasSize(1);
    }
  }

  @Nested
  @DisplayName("租约操作")
  class LeaseTests {

    @Test
    @DisplayName("应成功获取租约")
    void shouldAcquireLeaseSuccessfully() {
      // Given
      OutboxMessageDO messageDO = createTestOutboxMessageDO("dedup-001");
      outboxMessageMapper.insert(messageDO);

      Instant leaseExpireAt = TEST_NOW.plusSeconds(300);

      // When: 使用初始版本号 0L（MyBatis-Plus @Version 默认值）
      boolean result = repository.acquireLease(messageDO.getId(), 0L, TEST_OWNER, leaseExpireAt);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应在版本冲突时获取租约失败")
    void shouldFailToAcquireLeaseWhenVersionConflict() {
      // Given
      OutboxMessageDO messageDO = createTestOutboxMessageDO("dedup-001");
      outboxMessageMapper.insert(messageDO);

      Instant leaseExpireAt = TEST_NOW.plusSeconds(300);

      // When: 使用错误的版本号
      boolean result = repository.acquireLease(messageDO.getId(), 999L, TEST_OWNER, leaseExpireAt);

      // Then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("状态转换操作")
  class StateTransitionTests {

    @Test
    @DisplayName("应成功标记消息为已发布")
    void shouldMarkPublishedSuccessfully() {
      // Given: 插入消息并获取租约（转到 PUBLISHING 状态）
      OutboxMessageDO messageDO = createTestOutboxMessageDO("dedup-001");
      outboxMessageMapper.insert(messageDO);

      // 先获取租约（PENDING → PUBLISHING），版本从 0 变为 1
      repository.acquireLease(messageDO.getId(), 0L, TEST_OWNER, TEST_NOW.plusSeconds(300));

      // When: 使用获取租约后的版本号 1L
      repository.markPublished(messageDO.getId(), 1L);

      // Then
      Optional<OutboxMessage> updated = repository.findByChannelAndDedup(TEST_CHANNEL, "dedup-001");
      assertThat(updated).isPresent();
      assertThat(updated.get().getStatusCode()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("应在版本冲突时标记已发布失败并抛出异常")
    void shouldThrowExceptionWhenMarkPublishedFailsDueToVersionConflict() {
      // Given: 插入消息并获取租约（转到 PUBLISHING 状态）
      OutboxMessageDO messageDO = createTestOutboxMessageDO("dedup-001");
      outboxMessageMapper.insert(messageDO);
      repository.acquireLease(messageDO.getId(), 0L, TEST_OWNER, TEST_NOW.plusSeconds(300));

      // When & Then: 使用错误的版本号（正确版本是 1L）
      assertThatThrownBy(() -> repository.markPublished(messageDO.getId(), 999L))
          .isInstanceOf(OutboxPersistenceException.class)
          .hasMessageContaining("Failed to update Outbox state to PUBLISHED");
    }

    @Test
    @DisplayName("应成功标记消息为延迟重试")
    void shouldMarkDeferredSuccessfully() {
      // Given: 插入消息并获取租约（转到 PUBLISHING 状态）
      OutboxMessageDO messageDO = createTestOutboxMessageDO("dedup-001");
      outboxMessageMapper.insert(messageDO);
      repository.acquireLease(messageDO.getId(), 0L, TEST_OWNER, TEST_NOW.plusSeconds(300));

      Instant nextRetryAt = TEST_NOW.plusSeconds(60);

      // When: 使用获取租约后的版本号 1L
      repository.markDeferred(
          messageDO.getId(), 1L, 1, nextRetryAt, "NETWORK_ERROR", "Connection timeout");

      // Then
      Optional<OutboxMessage> updated = repository.findByChannelAndDedup(TEST_CHANNEL, "dedup-001");
      assertThat(updated).isPresent();
      assertThat(updated.get().getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("应成功标记消息为永久失败")
    void shouldMarkFailedSuccessfully() {
      // Given: 插入消息并获取租约（转到 PUBLISHING 状态）
      OutboxMessageDO messageDO = createTestOutboxMessageDO("dedup-001");
      outboxMessageMapper.insert(messageDO);
      repository.acquireLease(messageDO.getId(), 0L, TEST_OWNER, TEST_NOW.plusSeconds(300));

      // When: 使用获取租约后的版本号 1L
      repository.markFailed(
          messageDO.getId(), 1L, 3, "MAX_RETRIES_EXCEEDED", "Exhausted all retries");

      // Then
      Optional<OutboxMessage> updated = repository.findByChannelAndDedup(TEST_CHANNEL, "dedup-001");
      assertThat(updated).isPresent();
      assertThat(updated.get().getStatusCode()).isEqualTo("DEAD");
    }
  }

  @Nested
  @DisplayName("批量操作")
  class BatchOperationsTests {

    @Test
    @DisplayName("应批量查询消息")
    void shouldFindByChannelAndDedupIn() {
      // Given
      OutboxMessageDO message1 = createTestOutboxMessageDO("dedup-001");
      OutboxMessageDO message2 = createTestOutboxMessageDO("dedup-002");
      outboxMessageMapper.insert(message1);
      outboxMessageMapper.insert(message2);

      List<String> dedupKeys = List.of("dedup-001", "dedup-002");

      // When
      List<OutboxMessage> result = repository.findByChannelAndDedupIn(TEST_CHANNEL, dedupKeys);

      // Then
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("应在去重键列表为空时返回空列表")
    void shouldReturnEmptyListWhenDedupKeysIsEmpty() {
      // When
      List<OutboxMessage> result = repository.findByChannelAndDedupIn(TEST_CHANNEL, List.of());

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ==================== 辅助方法 ====================

  private OutboxMessageDO createTestOutboxMessageDO(String dedupKey) {
    OutboxMessageDO message = new OutboxMessageDO();
    message.setAggregateType("TASK");
    message.setAggregateId(1L);
    message.setChannel(TEST_CHANNEL);
    message.setOpType("TASK_READY");
    message.setPartitionKey("PUBMED:HARVEST");
    message.setDedupKey(dedupKey);
    message.setPayloadJson(createTestPayload());
    message.setStatusCode("PENDING");
    message.setRetryCount(0);
    return message;
  }

  private ObjectNode createTestPayload() {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("taskId", 1);
    payload.put("action", "TEST");
    return payload;
  }
}
