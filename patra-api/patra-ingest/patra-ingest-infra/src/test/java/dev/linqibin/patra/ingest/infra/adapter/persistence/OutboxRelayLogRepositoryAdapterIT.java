package dev.linqibin.patra.ingest.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.ingest.domain.model.entity.OutboxRelayLog;
import dev.linqibin.patra.ingest.domain.model.enums.RelayStatus;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.OutboxMessageDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.OutboxRelayLogDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.OutboxMessageEntity;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.OutboxRelayLogEntity;
import dev.linqibin.patra.ingest.infra.config.IngestPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

/// OutboxRelayLogRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + MySQL 8 测试发件箱中继日志持久化。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 MySQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：save、saveBatch、findByOutboxMessageId、findByBatchId、
///     countByChannelAndStatus、findRecentFailed、findByChannelAndTimeRange
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = IngestPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  OutboxRelayLogRepositoryAdapter.class,
  JacksonAutoConfiguration.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("OutboxRelayLogRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OutboxRelayLogRepositoryAdapterIT {

  @Autowired private OutboxRelayLogRepositoryAdapter repository;

  @Autowired private OutboxRelayLogDao logDao;
  @Autowired private OutboxMessageDao messageDao;
  @Autowired private ObjectMapper objectMapper;

  private static final String TEST_CHANNEL = "INGEST_TASK";
  private static final String TEST_PARTITION_KEY = "PUBMED:HARVEST";
  private static final String TEST_BATCH_ID = "20251128120000-a1b2c3d4";
  private static final String TEST_BATCH_ID_2 = "20251128130000-b2c3d4e5";
  private static final String TEST_LEASE_OWNER = "ingest-server-1-job123-thread456-abc123";

  private Long testMessageId;
  private Long testMessageId2;

  @BeforeEach
  void setUp() {
    // 清理现有数据（按外键依赖顺序）
    logDao.deleteAllInBatch();
    messageDao.deleteAllInBatch();

    // 创建测试用的发件箱消息
    testMessageId = insertOutboxMessage("dedup-key-1");
    testMessageId2 = insertOutboxMessage("dedup-key-2");
  }

  @Nested
  @DisplayName("save 操作")
  class SaveTests {

    @Test
    @DisplayName("应保存单条中继日志")
    void shouldSaveSingleRelayLog() {
      // Given
      OutboxRelayLog log = createRelayLog(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED, 1);

      // When
      repository.save(log);

      // Then
      List<OutboxRelayLogEntity> fromDb = logDao.findByMessageIdOrderByStartedAtDesc(testMessageId);
      assertThat(fromDb).hasSize(1);
      assertThat(fromDb.get(0).getChannel()).isEqualTo(TEST_CHANNEL);
      assertThat(fromDb.get(0).getRelayStatus()).isEqualTo(RelayStatus.PUBLISHED.getCode());
    }

    @Test
    @DisplayName("应拒绝 null 参数")
    void shouldRejectNullArgument() {
      // When & Then
      assertThatThrownBy(() -> repository.save(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");
    }
  }

  @Nested
  @DisplayName("saveBatch 操作")
  class SaveBatchTests {

    @Test
    @DisplayName("应处理空列表而不抛异常")
    void shouldHandleEmptyList() {
      // When & Then
      repository.saveBatch(List.of());
      // 不抛异常即为成功
    }

    @Test
    @DisplayName("应处理 null 列表而不抛异常")
    void shouldHandleNullList() {
      // When & Then
      repository.saveBatch(null);
      // 不抛异常即为成功
    }

    @Test
    @DisplayName("应批量插入所有中继日志")
    void shouldBatchInsertAllRelayLogs() {
      // Given
      List<OutboxRelayLog> logs =
          List.of(
              createRelayLog(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED, 1),
              createRelayLog(testMessageId2, TEST_BATCH_ID, RelayStatus.DEFERRED, 1),
              createRelayLog(testMessageId, TEST_BATCH_ID, RelayStatus.FAILED, 2));

      // When
      repository.saveBatch(logs);

      // Then
      List<OutboxRelayLogEntity> fromDb =
          logDao.findByRelayBatchIdOrderByStartedAtAsc(TEST_BATCH_ID);
      assertThat(fromDb).hasSize(3);
    }
  }

  @Nested
  @DisplayName("findByOutboxMessageId 操作")
  class FindByOutboxMessageIdTests {

    @Test
    @DisplayName("应按消息 ID 查询所有中继日志")
    void shouldFindAllLogsByMessageId() {
      // Given：为同一消息创建多个中继尝试
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.DEFERRED.getCode(), 1);
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 2);

      // When
      List<OutboxRelayLog> result = repository.findByOutboxMessageId(testMessageId);

      // Then
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("应在无匹配时返回空列表")
    void shouldReturnEmptyListWhenNoMatch() {
      // When
      List<OutboxRelayLog> result = repository.findByOutboxMessageId(999999L);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应拒绝 null 参数")
    void shouldRejectNullArgument() {
      // When & Then
      assertThatThrownBy(() -> repository.findByOutboxMessageId(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");
    }
  }

  @Nested
  @DisplayName("findByBatchId 操作")
  class FindByBatchIdTests {

    @Test
    @DisplayName("应按批次 ID 查询所有中继日志")
    void shouldFindAllLogsByBatchId() {
      // Given
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 1);
      insertRelayLogEntity(testMessageId2, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 1);
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID_2, RelayStatus.PUBLISHED.getCode(), 1);

      // When
      List<OutboxRelayLog> result = repository.findByBatchId(TEST_BATCH_ID);

      // Then
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("应在无匹配时返回空列表")
    void shouldReturnEmptyListWhenNoMatch() {
      // When
      List<OutboxRelayLog> result = repository.findByBatchId("nonexistent-batch-id");

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应拒绝空白参数")
    void shouldRejectBlankArgument() {
      // When & Then
      assertThatThrownBy(() -> repository.findByBatchId(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null or blank");
    }
  }

  @Nested
  @DisplayName("countByChannelAndStatus 操作")
  class CountByChannelAndStatusTests {

    @Test
    @DisplayName("应按通道和状态统计")
    void shouldCountByChannelAndStatus() {
      // Given
      Instant now = Instant.now();
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 1, now);
      insertRelayLogEntity(testMessageId2, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 1, now);
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.FAILED.getCode(), 2, now);

      // When
      long publishedCount =
          repository.countByChannelAndStatus(
              TEST_CHANNEL,
              RelayStatus.PUBLISHED.getCode(),
              now.minus(1, ChronoUnit.HOURS),
              now.plus(1, ChronoUnit.HOURS));

      long failedCount =
          repository.countByChannelAndStatus(
              TEST_CHANNEL,
              RelayStatus.FAILED.getCode(),
              now.minus(1, ChronoUnit.HOURS),
              now.plus(1, ChronoUnit.HOURS));

      // Then
      assertThat(publishedCount).isEqualTo(2);
      assertThat(failedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("应支持 null 通道（查询所有通道）")
    void shouldSupportNullChannel() {
      // Given
      Instant now = Instant.now();
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 1, now);

      // When
      long count =
          repository.countByChannelAndStatus(
              null,
              RelayStatus.PUBLISHED.getCode(),
              now.minus(1, ChronoUnit.HOURS),
              now.plus(1, ChronoUnit.HOURS));

      // Then
      assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("应拒绝 null 时间参数")
    void shouldRejectNullTimeArguments() {
      // When & Then
      assertThatThrownBy(
              () -> repository.countByChannelAndStatus(TEST_CHANNEL, null, null, Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");

      assertThatThrownBy(
              () -> repository.countByChannelAndStatus(TEST_CHANNEL, null, Instant.now(), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");
    }
  }

  @Nested
  @DisplayName("findRecentFailed 操作")
  class FindRecentFailedTests {

    @Test
    @DisplayName("应查询最近失败的中继日志")
    void shouldFindRecentFailed() {
      // Given
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.FAILED.getCode(), 1);
      insertRelayLogEntity(testMessageId2, TEST_BATCH_ID, RelayStatus.FAILED.getCode(), 1);
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 2);

      // When
      List<OutboxRelayLog> result = repository.findRecentFailed(TEST_CHANNEL, 10);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).allMatch(log -> log.getRelayStatus() == RelayStatus.FAILED);
    }

    @Test
    @DisplayName("应支持 null 通道（查询所有通道）")
    void shouldSupportNullChannel() {
      // Given
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.FAILED.getCode(), 1);

      // When
      List<OutboxRelayLog> result = repository.findRecentFailed(null, 10);

      // Then
      assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("应限制返回数量")
    void shouldRespectLimit() {
      // Given
      for (int i = 0; i < 5; i++) {
        Long msgId = insertOutboxMessage("dedup-key-limit-" + i);
        insertRelayLogEntity(msgId, TEST_BATCH_ID, RelayStatus.FAILED.getCode(), 1);
      }

      // When
      List<OutboxRelayLog> result = repository.findRecentFailed(TEST_CHANNEL, 3);

      // Then
      assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("应拒绝非正数 limit 参数")
    void shouldRejectNonPositiveLimit() {
      // When & Then
      assertThatThrownBy(() -> repository.findRecentFailed(TEST_CHANNEL, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("limit must be > 0");

      assertThatThrownBy(() -> repository.findRecentFailed(TEST_CHANNEL, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("limit must be > 0");
    }
  }

  @Nested
  @DisplayName("findByChannelAndTimeRange 操作")
  class FindByChannelAndTimeRangeTests {

    @Test
    @DisplayName("应按通道和时间范围查询")
    void shouldFindByChannelAndTimeRange() {
      // Given
      Instant now = Instant.now();
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 1, now);
      insertRelayLogEntity(testMessageId2, TEST_BATCH_ID, RelayStatus.DEFERRED.getCode(), 1, now);

      // When
      List<OutboxRelayLog> result =
          repository.findByChannelAndTimeRange(
              TEST_CHANNEL, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), 10);

      // Then
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("应支持 null 通道（查询所有通道）")
    void shouldSupportNullChannel() {
      // Given
      Instant now = Instant.now();
      insertRelayLogEntity(testMessageId, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 1, now);

      // When
      List<OutboxRelayLog> result =
          repository.findByChannelAndTimeRange(
              null, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), 10);

      // Then
      assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("应限制返回数量")
    void shouldRespectLimit() {
      // Given
      Instant now = Instant.now();
      for (int i = 0; i < 5; i++) {
        Long msgId = insertOutboxMessage("dedup-key-range-" + i);
        insertRelayLogEntity(msgId, TEST_BATCH_ID, RelayStatus.PUBLISHED.getCode(), 1, now);
      }

      // When
      List<OutboxRelayLog> result =
          repository.findByChannelAndTimeRange(
              TEST_CHANNEL, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), 3);

      // Then
      assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("应拒绝 null 时间参数")
    void shouldRejectNullTimeArguments() {
      // When & Then
      assertThatThrownBy(
              () -> repository.findByChannelAndTimeRange(TEST_CHANNEL, null, Instant.now(), 10))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");

      assertThatThrownBy(
              () -> repository.findByChannelAndTimeRange(TEST_CHANNEL, Instant.now(), null, 10))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("应拒绝非正数 limit 参数")
    void shouldRejectNonPositiveLimit() {
      // When & Then
      assertThatThrownBy(
              () ->
                  repository.findByChannelAndTimeRange(
                      TEST_CHANNEL, Instant.now().minusSeconds(3600), Instant.now(), 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("limit must be > 0");
    }
  }

  // ==================== 辅助方法 ====================

  private Long insertOutboxMessage(String dedupKey) {
    OutboxMessageEntity message = new OutboxMessageEntity();
    message.setId(SnowflakeIdGenerator.getId());
    message.setAggregateType("TASK");
    message.setAggregateId(1L);
    message.setChannel(TEST_CHANNEL);
    message.setOpType("TASK_READY");
    message.setPartitionKey(TEST_PARTITION_KEY);
    message.setDedupKey(dedupKey);
    message.setPayloadJson(createTestPayload());
    message.setStatusCode("PENDING");
    message.setRetryCount(0);
    messageDao.saveAndFlush(message);
    return message.getId();
  }

  private ObjectNode createTestPayload() {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("taskId", 1L);
    payload.put("operation", "HARVEST");
    payload.put("provenance", "PUBMED");
    return payload;
  }

  private void insertRelayLogEntity(Long messageId, String batchId, String status, int attemptNo) {
    insertRelayLogEntity(messageId, batchId, status, attemptNo, Instant.now());
  }

  private void insertRelayLogEntity(
      Long messageId, String batchId, String status, int attemptNo, Instant startedAt) {
    OutboxRelayLogEntity log = new OutboxRelayLogEntity();
    log.setId(SnowflakeIdGenerator.getId());
    log.setMessageId(messageId);
    log.setRelayBatchId(batchId);
    log.setChannel(TEST_CHANNEL);
    log.setPartitionKey(TEST_PARTITION_KEY);
    log.setLeaseOwner(TEST_LEASE_OWNER);
    log.setAttemptNumber(attemptNo);
    log.setRelayStatus(status);
    log.setStartedAt(startedAt);
    log.setCompletedAt(startedAt.plusMillis(100));
    log.setDurationMs(100);
    if (RelayStatus.FAILED.getCode().equals(status)) {
      log.setErrorCode("TEST_ERROR");
      log.setErrorMessage("Test error message");
      log.setErrorKind("TRANSIENT");
    }
    logDao.saveAndFlush(log);
  }

  private OutboxRelayLog createRelayLog(
      Long messageId, String batchId, RelayStatus status, int attemptNo) {
    Instant now = Instant.now();
    return OutboxRelayLog.builder()
        .outboxMessageId(messageId)
        .relayBatchId(batchId)
        .channel(TEST_CHANNEL)
        .partitionKey(TEST_PARTITION_KEY)
        .leaseOwner(TEST_LEASE_OWNER)
        .attemptNumber(attemptNo)
        .relayStatus(status)
        .startedAt(now)
        .completedAt(now.plusMillis(50))
        .durationMs(50)
        .errorCode(status == RelayStatus.FAILED ? "TEST_ERROR" : null)
        .errorMessage(status == RelayStatus.FAILED ? "Test error" : null)
        .errorKind(status == RelayStatus.FAILED ? "TRANSIENT" : null)
        .nextRetryAt(status == RelayStatus.DEFERRED ? now.plusSeconds(60) : null)
        .build();
  }
}
