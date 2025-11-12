package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.model.enums.RelayStatus;
import com.patra.ingest.infra.persistence.entity.OutboxRelayLogDO;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * OutboxRelayLogConverter 单元测试。
 *
 * <p>测试 Domain 实体与 DO 之间的双向转换,包括:
 *
 * <ul>
 *   <li>字段映射正确性(outboxMessageId ↔ messageId)
 *   <li>枚举转换(RelayStatus ↔ String)
 *   <li>所有 RelayStatus 枚举值的转换
 *   <li>批量转换功能
 *   <li>Null 值处理
 * </ul>
 *
 * @author Patra Team
 * @since 0.1.0
 */
@DisplayName("OutboxRelayLogConverter 单元测试")
class OutboxRelayLogConverterTest {

  private final OutboxRelayLogConverter converter = new OutboxRelayLogConverterImpl();

  // 测试常量
  private static final Long MESSAGE_ID = 12345L;
  private static final String RELAY_BATCH_ID = "20251105-abc123";
  private static final String CHANNEL = "INGEST_TASK_READY";
  private static final String PARTITION_KEY = "PUBMED:HARVEST";
  private static final String LEASE_OWNER = "ingest-server-1-job123-thread456-uuid";
  private static final Integer ATTEMPT_NUMBER = 3;
  private static final String ERROR_CODE = "NETWORK_TIMEOUT";
  private static final String ERROR_MESSAGE = "Connection timeout after 5000ms";
  private static final String ERROR_KIND = "TRANSIENT";
  private static final Instant STARTED_AT = Instant.parse("2025-01-15T10:30:00Z");
  private static final Instant COMPLETED_AT = Instant.parse("2025-01-15T10:30:05Z");
  private static final Integer DURATION_MS = 5000;
  private static final Instant NEXT_RETRY_AT = Instant.parse("2025-01-15T10:35:00Z");

  @Nested
  @DisplayName("toEntity() 转换测试")
  class ToEntityConversionTests {

    @Test
    @DisplayName("应该正确转换 PUBLISHED 状态的中继日志")
    void shouldConvertPublishedRelayLogToEntity() {
      // Given: 创建 PUBLISHED 状态的领域实体
      OutboxRelayLog log =
          OutboxRelayLog.builder()
              .id(100L)
              .outboxMessageId(MESSAGE_ID)
              .relayBatchId(RELAY_BATCH_ID)
              .channel(CHANNEL)
              .partitionKey(PARTITION_KEY)
              .leaseOwner(LEASE_OWNER)
              .attemptNumber(ATTEMPT_NUMBER)
              .relayStatus(RelayStatus.PUBLISHED)
              .errorCode(null) // 成功状态无错误
              .errorMessage(null)
              .errorKind(null)
              .startedAt(STARTED_AT)
              .completedAt(COMPLETED_AT)
              .durationMs(DURATION_MS)
              .nextRetryAt(null) // 成功状态无重试
              .build();

      // When: 转换为 DO
      OutboxRelayLogDO result = converter.toEntity(log);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getMessageId()).isEqualTo(MESSAGE_ID);
      assertThat(result.getRelayBatchId()).isEqualTo(RELAY_BATCH_ID);
      assertThat(result.getChannel()).isEqualTo(CHANNEL);
      assertThat(result.getPartitionKey()).isEqualTo(PARTITION_KEY);
      assertThat(result.getLeaseOwner()).isEqualTo(LEASE_OWNER);
      assertThat(result.getAttemptNumber()).isEqualTo(ATTEMPT_NUMBER);
      assertThat(result.getRelayStatus()).isEqualTo("PUBLISHED");
      assertThat(result.getErrorCode()).isNull();
      assertThat(result.getErrorMessage()).isNull();
      assertThat(result.getErrorKind()).isNull();
      assertThat(result.getStartedAt()).isEqualTo(STARTED_AT);
      assertThat(result.getCompletedAt()).isEqualTo(COMPLETED_AT);
      assertThat(result.getDurationMs()).isEqualTo(DURATION_MS);
      assertThat(result.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("应该正确转换 DEFERRED 状态的中继日志")
    void shouldConvertDeferredRelayLogToEntity() {
      // Given: 创建 DEFERRED 状态的领域实体(包含错误信息和重试时间)
      OutboxRelayLog log =
          OutboxRelayLog.builder()
              .id(100L)
              .outboxMessageId(MESSAGE_ID)
              .relayBatchId(RELAY_BATCH_ID)
              .channel(CHANNEL)
              .partitionKey(PARTITION_KEY)
              .leaseOwner(LEASE_OWNER)
              .attemptNumber(ATTEMPT_NUMBER)
              .relayStatus(RelayStatus.DEFERRED)
              .errorCode(ERROR_CODE)
              .errorMessage(ERROR_MESSAGE)
              .errorKind(ERROR_KIND)
              .startedAt(STARTED_AT)
              .completedAt(COMPLETED_AT)
              .durationMs(DURATION_MS)
              .nextRetryAt(NEXT_RETRY_AT)
              .build();

      // When: 转换为 DO
      OutboxRelayLogDO result = converter.toEntity(log);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getRelayStatus()).isEqualTo("DEFERRED");
      assertThat(result.getErrorCode()).isEqualTo(ERROR_CODE);
      assertThat(result.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
      assertThat(result.getErrorKind()).isEqualTo(ERROR_KIND);
      assertThat(result.getNextRetryAt()).isEqualTo(NEXT_RETRY_AT);
    }

    @Test
    @DisplayName("应该正确转换 FAILED 状态的中继日志")
    void shouldConvertFailedRelayLogToEntity() {
      // Given: 创建 FAILED 状态的领域实体
      OutboxRelayLog log =
          OutboxRelayLog.builder()
              .id(100L)
              .outboxMessageId(MESSAGE_ID)
              .relayBatchId(RELAY_BATCH_ID)
              .channel(CHANNEL)
              .partitionKey(PARTITION_KEY)
              .leaseOwner(LEASE_OWNER)
              .attemptNumber(5) // 达到最大重试次数
              .relayStatus(RelayStatus.FAILED)
              .errorCode("MAX_RETRIES_EXCEEDED")
              .errorMessage("Failed after 5 attempts")
              .errorKind("FATAL")
              .startedAt(STARTED_AT)
              .completedAt(COMPLETED_AT)
              .durationMs(DURATION_MS)
              .nextRetryAt(null)
              .build();

      // When: 转换为 DO
      OutboxRelayLogDO result = converter.toEntity(log);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getRelayStatus()).isEqualTo("FAILED");
      assertThat(result.getAttemptNumber()).isEqualTo(5);
      assertThat(result.getErrorCode()).isEqualTo("MAX_RETRIES_EXCEEDED");
      assertThat(result.getErrorKind()).isEqualTo("FATAL");
    }

    @Test
    @DisplayName("应该正确转换 LEASE_MISSED 状态的中继日志")
    void shouldConvertLeaseMissedRelayLogToEntity() {
      // Given: 创建 LEASE_MISSED 状态的领域实体
      OutboxRelayLog log =
          OutboxRelayLog.builder()
              .id(100L)
              .outboxMessageId(MESSAGE_ID)
              .relayBatchId(RELAY_BATCH_ID)
              .channel(CHANNEL)
              .partitionKey(PARTITION_KEY)
              .leaseOwner(LEASE_OWNER)
              .attemptNumber(1)
              .relayStatus(RelayStatus.LEASE_MISSED)
              .errorCode("LEASE_CONFLICT")
              .errorMessage("Another instance acquired the lease")
              .errorKind("TRANSIENT")
              .startedAt(STARTED_AT)
              .completedAt(COMPLETED_AT)
              .durationMs(100) // 快速失败
              .nextRetryAt(null)
              .build();

      // When: 转换为 DO
      OutboxRelayLogDO result = converter.toEntity(log);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getRelayStatus()).isEqualTo("LEASE_MISSED");
      assertThat(result.getDurationMs()).isEqualTo(100);
    }

    @Test
    @DisplayName("应该正确处理 null 的可选字段")
    void shouldHandleNullOptionalFields() {
      // Given: 创建包含 null 可选字段的领域实体
      OutboxRelayLog log =
          OutboxRelayLog.builder()
              .id(null) // ID 可能为 null(新创建的实体)
              .outboxMessageId(MESSAGE_ID)
              .relayBatchId(RELAY_BATCH_ID)
              .channel(CHANNEL)
              .partitionKey(null) // 可选字段
              .leaseOwner(LEASE_OWNER)
              .attemptNumber(ATTEMPT_NUMBER)
              .relayStatus(RelayStatus.PUBLISHED)
              .errorCode(null)
              .errorMessage(null)
              .errorKind(null)
              .startedAt(STARTED_AT)
              .completedAt(COMPLETED_AT)
              .durationMs(DURATION_MS)
              .nextRetryAt(null)
              .build();

      // When: 转换为 DO
      OutboxRelayLogDO result = converter.toEntity(log);

      // Then: 验证 null 值正确传递
      assertThat(result).isNotNull();
      assertThat(result.getPartitionKey()).isNull();
      assertThat(result.getErrorCode()).isNull();
      assertThat(result.getErrorMessage()).isNull();
      assertThat(result.getErrorKind()).isNull();
      assertThat(result.getNextRetryAt()).isNull();
    }
  }

  @Nested
  @DisplayName("toDomain() 转换测试")
  class ToDomainConversionTests {

    @Test
    @DisplayName("应该正确转换 PUBLISHED 状态的 DO")
    void shouldConvertPublishedEntityToDomain() {
      // Given: 创建 PUBLISHED 状态的 DO
      OutboxRelayLogDO entity = new OutboxRelayLogDO();
      entity.setId(100L);
      entity.setMessageId(MESSAGE_ID);
      entity.setRelayBatchId(RELAY_BATCH_ID);
      entity.setChannel(CHANNEL);
      entity.setPartitionKey(PARTITION_KEY);
      entity.setLeaseOwner(LEASE_OWNER);
      entity.setAttemptNumber(ATTEMPT_NUMBER);
      entity.setRelayStatus("PUBLISHED");
      entity.setStartedAt(STARTED_AT);
      entity.setCompletedAt(COMPLETED_AT);
      entity.setDurationMs(DURATION_MS);

      // When: 转换为领域实体
      OutboxRelayLog result = converter.toDomain(entity);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(100L);
      assertThat(result.getOutboxMessageId()).isEqualTo(MESSAGE_ID);
      assertThat(result.getRelayBatchId()).isEqualTo(RELAY_BATCH_ID);
      assertThat(result.getChannel()).isEqualTo(CHANNEL);
      assertThat(result.getPartitionKey()).isEqualTo(PARTITION_KEY);
      assertThat(result.getLeaseOwner()).isEqualTo(LEASE_OWNER);
      assertThat(result.getAttemptNumber()).isEqualTo(ATTEMPT_NUMBER);
      assertThat(result.getRelayStatus()).isEqualTo(RelayStatus.PUBLISHED);
      assertThat(result.getStartedAt()).isEqualTo(STARTED_AT);
      assertThat(result.getCompletedAt()).isEqualTo(COMPLETED_AT);
      assertThat(result.getDurationMs()).isEqualTo(DURATION_MS);
    }

    @Test
    @DisplayName("应该正确转换 DEFERRED 状态的 DO")
    void shouldConvertDeferredEntityToDomain() {
      // Given: 创建 DEFERRED 状态的 DO
      OutboxRelayLogDO entity = new OutboxRelayLogDO();
      entity.setId(100L);
      entity.setMessageId(MESSAGE_ID);
      entity.setRelayBatchId(RELAY_BATCH_ID);
      entity.setChannel(CHANNEL);
      entity.setPartitionKey(PARTITION_KEY);
      entity.setLeaseOwner(LEASE_OWNER);
      entity.setAttemptNumber(ATTEMPT_NUMBER);
      entity.setRelayStatus("DEFERRED");
      entity.setErrorCode(ERROR_CODE);
      entity.setErrorMessage(ERROR_MESSAGE);
      entity.setErrorKind(ERROR_KIND);
      entity.setStartedAt(STARTED_AT);
      entity.setCompletedAt(COMPLETED_AT);
      entity.setDurationMs(DURATION_MS);
      entity.setNextRetryAt(NEXT_RETRY_AT);

      // When: 转换为领域实体
      OutboxRelayLog result = converter.toDomain(entity);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getRelayStatus()).isEqualTo(RelayStatus.DEFERRED);
      assertThat(result.getErrorCode()).isEqualTo(ERROR_CODE);
      assertThat(result.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
      assertThat(result.getErrorKind()).isEqualTo(ERROR_KIND);
      assertThat(result.getNextRetryAt()).isEqualTo(NEXT_RETRY_AT);
    }

    @Test
    @DisplayName("应该正确转换 FAILED 状态的 DO")
    void shouldConvertFailedEntityToDomain() {
      // Given: 创建 FAILED 状态的 DO
      OutboxRelayLogDO entity = new OutboxRelayLogDO();
      entity.setId(100L);
      entity.setMessageId(MESSAGE_ID);
      entity.setRelayBatchId(RELAY_BATCH_ID);
      entity.setChannel(CHANNEL);
      entity.setPartitionKey(PARTITION_KEY);
      entity.setLeaseOwner(LEASE_OWNER);
      entity.setAttemptNumber(5);
      entity.setRelayStatus("FAILED");
      entity.setErrorCode("MAX_RETRIES_EXCEEDED");
      entity.setErrorMessage("Failed after 5 attempts");
      entity.setErrorKind("FATAL");
      entity.setStartedAt(STARTED_AT);
      entity.setCompletedAt(COMPLETED_AT);
      entity.setDurationMs(DURATION_MS);

      // When: 转换为领域实体
      OutboxRelayLog result = converter.toDomain(entity);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getRelayStatus()).isEqualTo(RelayStatus.FAILED);
      assertThat(result.getAttemptNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("应该正确转换 LEASE_MISSED 状态的 DO")
    void shouldConvertLeaseMissedEntityToDomain() {
      // Given: 创建 LEASE_MISSED 状态的 DO
      OutboxRelayLogDO entity = new OutboxRelayLogDO();
      entity.setId(100L);
      entity.setMessageId(MESSAGE_ID);
      entity.setRelayBatchId(RELAY_BATCH_ID);
      entity.setChannel(CHANNEL);
      entity.setPartitionKey(PARTITION_KEY);
      entity.setLeaseOwner(LEASE_OWNER);
      entity.setAttemptNumber(1);
      entity.setRelayStatus("LEASE_MISSED");
      entity.setStartedAt(STARTED_AT);
      entity.setCompletedAt(COMPLETED_AT);
      entity.setDurationMs(100);

      // When: 转换为领域实体
      OutboxRelayLog result = converter.toDomain(entity);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getRelayStatus()).isEqualTo(RelayStatus.LEASE_MISSED);
    }
  }

  @Nested
  @DisplayName("批量转换测试")
  class BatchConversionTests {

    @Test
    @DisplayName("应该正确批量转换领域实体列表为 DO 列表")
    void shouldConvertDomainListToEntityList() {
      // Given: 创建多个不同状态的领域实体
      OutboxRelayLog log1 =
          OutboxRelayLog.builder()
              .id(100L)
              .outboxMessageId(MESSAGE_ID)
              .relayBatchId(RELAY_BATCH_ID)
              .channel(CHANNEL)
              .partitionKey(PARTITION_KEY)
              .leaseOwner(LEASE_OWNER)
              .attemptNumber(1)
              .relayStatus(RelayStatus.PUBLISHED)
              .startedAt(STARTED_AT)
              .completedAt(COMPLETED_AT)
              .durationMs(DURATION_MS)
              .build();

      OutboxRelayLog log2 =
          OutboxRelayLog.builder()
              .id(101L)
              .outboxMessageId(MESSAGE_ID + 1)
              .relayBatchId(RELAY_BATCH_ID)
              .channel(CHANNEL)
              .partitionKey(PARTITION_KEY)
              .leaseOwner(LEASE_OWNER)
              .attemptNumber(2)
              .relayStatus(RelayStatus.DEFERRED)
              .errorCode(ERROR_CODE)
              .errorMessage(ERROR_MESSAGE)
              .errorKind(ERROR_KIND)
              .startedAt(STARTED_AT)
              .completedAt(COMPLETED_AT)
              .durationMs(DURATION_MS)
              .nextRetryAt(NEXT_RETRY_AT)
              .build();

      List<OutboxRelayLog> logs = List.of(log1, log2);

      // When: 批量转换为 DO 列表
      List<OutboxRelayLogDO> results = converter.toEntities(logs);

      // Then: 验证转换结果
      assertThat(results).hasSize(2);
      assertThat(results.get(0).getMessageId()).isEqualTo(MESSAGE_ID);
      assertThat(results.get(0).getRelayStatus()).isEqualTo("PUBLISHED");
      assertThat(results.get(1).getMessageId()).isEqualTo(MESSAGE_ID + 1);
      assertThat(results.get(1).getRelayStatus()).isEqualTo("DEFERRED");
    }

    @Test
    @DisplayName("应该正确批量转换 DO 列表为领域实体列表")
    void shouldConvertEntityListToDomainList() {
      // Given: 创建多个 DO
      OutboxRelayLogDO entity1 = new OutboxRelayLogDO();
      entity1.setId(100L);
      entity1.setMessageId(MESSAGE_ID);
      entity1.setRelayBatchId(RELAY_BATCH_ID);
      entity1.setChannel(CHANNEL);
      entity1.setPartitionKey(PARTITION_KEY);
      entity1.setLeaseOwner(LEASE_OWNER);
      entity1.setAttemptNumber(1);
      entity1.setRelayStatus("PUBLISHED");
      entity1.setStartedAt(STARTED_AT);
      entity1.setCompletedAt(COMPLETED_AT);
      entity1.setDurationMs(DURATION_MS);

      OutboxRelayLogDO entity2 = new OutboxRelayLogDO();
      entity2.setId(101L);
      entity2.setMessageId(MESSAGE_ID + 1);
      entity2.setRelayBatchId(RELAY_BATCH_ID);
      entity2.setChannel(CHANNEL);
      entity2.setPartitionKey(PARTITION_KEY);
      entity2.setLeaseOwner(LEASE_OWNER);
      entity2.setAttemptNumber(1);
      entity2.setRelayStatus("FAILED");
      entity2.setStartedAt(STARTED_AT);
      entity2.setCompletedAt(COMPLETED_AT);
      entity2.setDurationMs(DURATION_MS);

      List<OutboxRelayLogDO> entities = List.of(entity1, entity2);

      // When: 批量转换为领域实体列表
      List<OutboxRelayLog> results = converter.toDomains(entities);

      // Then: 验证转换结果
      assertThat(results).hasSize(2);
      assertThat(results.get(0).getOutboxMessageId()).isEqualTo(MESSAGE_ID);
      assertThat(results.get(0).getRelayStatus()).isEqualTo(RelayStatus.PUBLISHED);
      assertThat(results.get(1).getOutboxMessageId()).isEqualTo(MESSAGE_ID + 1);
      assertThat(results.get(1).getRelayStatus()).isEqualTo(RelayStatus.FAILED);
    }

    @Test
    @DisplayName("应该正确处理空列表")
    void shouldHandleEmptyLists() {
      // Given: 空列表
      List<OutboxRelayLog> emptyDomainList = List.of();
      List<OutboxRelayLogDO> emptyEntityList = List.of();

      // When & Then: 批量转换空列表应返回空列表
      assertThat(converter.toEntities(emptyDomainList)).isEmpty();
      assertThat(converter.toDomains(emptyEntityList)).isEmpty();
    }
  }

  @Nested
  @DisplayName("双向转换一致性测试")
  class RoundTripConsistencyTests {

    @Test
    @DisplayName("应该保证双向转换的一致性")
    void shouldMaintainConsistencyInRoundTripConversion() {
      // Given: 创建完整的领域实体
      OutboxRelayLog original =
          OutboxRelayLog.builder()
              .id(100L)
              .outboxMessageId(MESSAGE_ID)
              .relayBatchId(RELAY_BATCH_ID)
              .channel(CHANNEL)
              .partitionKey(PARTITION_KEY)
              .leaseOwner(LEASE_OWNER)
              .attemptNumber(ATTEMPT_NUMBER)
              .relayStatus(RelayStatus.DEFERRED)
              .errorCode(ERROR_CODE)
              .errorMessage(ERROR_MESSAGE)
              .errorKind(ERROR_KIND)
              .startedAt(STARTED_AT)
              .completedAt(COMPLETED_AT)
              .durationMs(DURATION_MS)
              .nextRetryAt(NEXT_RETRY_AT)
              .build();

      // When: 双向转换(Domain → DO → Domain)
      OutboxRelayLogDO entity = converter.toEntity(original);
      OutboxRelayLog result = converter.toDomain(entity);

      // Then: 验证转换后的值与原始值一致(除了 id,因为 MapStruct 不映射 id 到 DO)
      assertThat(result.getOutboxMessageId()).isEqualTo(original.getOutboxMessageId());
      assertThat(result.getRelayBatchId()).isEqualTo(original.getRelayBatchId());
      assertThat(result.getChannel()).isEqualTo(original.getChannel());
      assertThat(result.getPartitionKey()).isEqualTo(original.getPartitionKey());
      assertThat(result.getLeaseOwner()).isEqualTo(original.getLeaseOwner());
      assertThat(result.getAttemptNumber()).isEqualTo(original.getAttemptNumber());
      assertThat(result.getRelayStatus()).isEqualTo(original.getRelayStatus());
      assertThat(result.getErrorCode()).isEqualTo(original.getErrorCode());
      assertThat(result.getErrorMessage()).isEqualTo(original.getErrorMessage());
      assertThat(result.getErrorKind()).isEqualTo(original.getErrorKind());
      assertThat(result.getStartedAt()).isEqualTo(original.getStartedAt());
      assertThat(result.getCompletedAt()).isEqualTo(original.getCompletedAt());
      assertThat(result.getDurationMs()).isEqualTo(original.getDurationMs());
      assertThat(result.getNextRetryAt()).isEqualTo(original.getNextRetryAt());
    }
  }
}
