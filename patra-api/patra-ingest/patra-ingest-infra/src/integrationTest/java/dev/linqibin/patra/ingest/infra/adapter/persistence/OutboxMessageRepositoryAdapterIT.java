package dev.linqibin.patra.ingest.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.ingest.domain.exception.OutboxPersistenceException;
import dev.linqibin.patra.ingest.domain.model.entity.OutboxMessage;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.OutboxMessageDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.OutboxMessageEntity;
import dev.linqibin.patra.ingest.infra.config.IngestITPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/// OutboxMessageRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + PostgreSQL 17 测试发件箱消息持久化。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 PostgreSQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 PostgreSQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：saveAll、saveOrUpdate、findByChannelAndDedup、fetchPending、租约操作、状态转换
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = IngestITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OutboxMessageRepositoryAdapter.class,
  JacksonAutoConfiguration.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("OutboxMessageRepositoryAdapter 集成测试")
class OutboxMessageRepositoryAdapterIT {

  @Autowired private OutboxMessageRepositoryAdapter repository;

  @Autowired private OutboxMessageDao outboxMessageDao;
  @Autowired private ObjectMapper objectMapper;

  private static final String TEST_CHANNEL = "ingest.task";
  private static final String TEST_OWNER = "relay-01";
  private static final Instant TEST_NOW = Instant.parse("2025-01-01T00:00:00Z");

  @BeforeEach
  void setUp() {
    // 清理现有数据
    outboxMessageDao.deleteAllInBatch();
  }

  @Nested
  @DisplayName("查询操作")
  class FindTests {

    @Test
    @DisplayName("应按通道和去重键查找消息")
    void shouldFindByChannelAndDedup() {
      // Given
      OutboxMessageEntity entity = createTestOutboxMessageEntity("dedup-001");
      outboxMessageDao.saveAndFlush(entity);

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
      OutboxMessageEntity message1 = createTestOutboxMessageEntity("dedup-001");
      OutboxMessageEntity message2 = createTestOutboxMessageEntity("dedup-002");
      outboxMessageDao.saveAndFlush(message1);
      outboxMessageDao.saveAndFlush(message2);

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
      OutboxMessageEntity entity = createTestOutboxMessageEntity("dedup-001");
      outboxMessageDao.saveAndFlush(entity);

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
      OutboxMessageEntity entity = createTestOutboxMessageEntity("dedup-001");
      outboxMessageDao.saveAndFlush(entity);

      Instant leaseExpireAt = TEST_NOW.plusSeconds(300);

      // When: 使用初始版本号 0L（JPA @Version 默认值）
      boolean result = repository.acquireLease(entity.getId(), 0L, TEST_OWNER, leaseExpireAt);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应在版本冲突时获取租约失败")
    void shouldFailToAcquireLeaseWhenVersionConflict() {
      // Given
      OutboxMessageEntity entity = createTestOutboxMessageEntity("dedup-001");
      outboxMessageDao.saveAndFlush(entity);

      Instant leaseExpireAt = TEST_NOW.plusSeconds(300);

      // When: 使用错误的版本号
      boolean result = repository.acquireLease(entity.getId(), 999L, TEST_OWNER, leaseExpireAt);

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
      OutboxMessageEntity entity = createTestOutboxMessageEntity("dedup-001");
      outboxMessageDao.saveAndFlush(entity);

      // 先获取租约（PENDING → PUBLISHING），版本从 0 变为 1
      repository.acquireLease(entity.getId(), 0L, TEST_OWNER, TEST_NOW.plusSeconds(300));

      // When: 使用获取租约后的版本号 1L
      repository.markPublished(entity.getId(), 1L);

      // Then
      Optional<OutboxMessage> updated = repository.findByChannelAndDedup(TEST_CHANNEL, "dedup-001");
      assertThat(updated).isPresent();
      assertThat(updated.get().getStatusCode()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("应在版本冲突时标记已发布失败并抛出异常")
    void shouldThrowExceptionWhenMarkPublishedFailsDueToVersionConflict() {
      // Given: 插入消息并获取租约（转到 PUBLISHING 状态）
      OutboxMessageEntity entity = createTestOutboxMessageEntity("dedup-001");
      outboxMessageDao.saveAndFlush(entity);
      repository.acquireLease(entity.getId(), 0L, TEST_OWNER, TEST_NOW.plusSeconds(300));

      // When & Then: 使用错误的版本号（正确版本是 1L）
      assertThatThrownBy(() -> repository.markPublished(entity.getId(), 999L))
          .isInstanceOf(OutboxPersistenceException.class)
          .hasMessageContaining("Failed to update Outbox state to PUBLISHED");
    }

    @Test
    @DisplayName("应成功标记消息为延迟重试")
    void shouldMarkDeferredSuccessfully() {
      // Given: 插入消息并获取租约（转到 PUBLISHING 状态）
      OutboxMessageEntity entity = createTestOutboxMessageEntity("dedup-001");
      outboxMessageDao.saveAndFlush(entity);
      repository.acquireLease(entity.getId(), 0L, TEST_OWNER, TEST_NOW.plusSeconds(300));

      Instant nextRetryAt = TEST_NOW.plusSeconds(60);

      // When: 使用获取租约后的版本号 1L
      repository.markDeferred(
          entity.getId(), 1L, 1, nextRetryAt, "NETWORK_ERROR", "Connection timeout");

      // Then
      Optional<OutboxMessage> updated = repository.findByChannelAndDedup(TEST_CHANNEL, "dedup-001");
      assertThat(updated).isPresent();
      assertThat(updated.get().getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("应成功标记消息为永久失败")
    void shouldMarkFailedSuccessfully() {
      // Given: 插入消息并获取租约（转到 PUBLISHING 状态）
      OutboxMessageEntity entity = createTestOutboxMessageEntity("dedup-001");
      outboxMessageDao.saveAndFlush(entity);
      repository.acquireLease(entity.getId(), 0L, TEST_OWNER, TEST_NOW.plusSeconds(300));

      // When: 使用获取租约后的版本号 1L
      repository.markFailed(entity.getId(), 1L, 3, "MAX_RETRIES_EXCEEDED", "Exhausted all retries");

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
      OutboxMessageEntity message1 = createTestOutboxMessageEntity("dedup-001");
      OutboxMessageEntity message2 = createTestOutboxMessageEntity("dedup-002");
      outboxMessageDao.saveAndFlush(message1);
      outboxMessageDao.saveAndFlush(message2);

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

  private OutboxMessageEntity createTestOutboxMessageEntity(String dedupKey) {
    OutboxMessageEntity message = new OutboxMessageEntity();
    message.setId(SnowflakeIdGenerator.getId());
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
