package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.batch.BatchStats;
import com.patra.ingest.domain.model.vo.shared.IdempotentKey;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import com.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * TaskRunBatchConverter 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>测试 TaskRunBatch → TaskRunBatchDO 的转换
 *   <li>测试 TaskRunBatchDO → TaskRunBatch 的转换
 *   <li>测试双向转换的一致性
 *   <li>测试 IdempotentKey 与 String 的双向转换
 *   <li>测试 BatchStats 与 recordCount + JSON 的转换
 *   <li>测试状态枚举转换
 *   <li>测试空值和边界情况
 * </ul>
 *
 * <p>注意：MapStruct 转换器通过 Mappers.getMapper() 直接实例化，无需 Spring 容器。
 */
class TaskRunBatchConverterTest {

  private final TaskRunBatchConverter converter = Mappers.getMapper(TaskRunBatchConverter.class);

  @Test
  @DisplayName("应当正确将TaskRunBatch转换为TaskRunBatchDO")
  void shouldConvertDomainToEntity() {
    // Given: 构造完整的TaskRunBatch
    Instant now = Instant.now();
    IdempotentKey idempotentKey = new IdempotentKey("a".repeat(64));
    BatchStats stats = BatchStats.of(150);

    TaskRunBatch batch =
        TaskRunBatch.restore(
            1001L,
            2001L,
            3001L,
            4001L,
            5001L,
            ProvenanceCode.PUBMED,
            "HARVEST",
            1,
            1,
            100,
            "before-token-abc",
            "after-token-xyz",
            "expr-hash-001",
            idempotentKey,
            BatchStatus.SUCCEEDED,
            stats,
            now,
            null,
            "s3://bucket/key");

    // When: 转换为DO
    TaskRunBatchDO entity = converter.toDO(batch);

    // Then: 验证所有字段正确映射
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(1001L);
    assertThat(entity.getRunId()).isEqualTo(2001L);
    assertThat(entity.getTaskId()).isEqualTo(3001L);
    assertThat(entity.getSliceId()).isEqualTo(4001L);
    assertThat(entity.getPlanId()).isEqualTo(5001L);
    assertThat(entity.getProvenanceCode()).isEqualTo("PUBMED");
    assertThat(entity.getOperationCode()).isEqualTo("HARVEST");
    assertThat(entity.getBatchNo()).isEqualTo(1);
    assertThat(entity.getPageNo()).isEqualTo(1);
    assertThat(entity.getPageSize()).isEqualTo(100);
    assertThat(entity.getBeforeToken()).isEqualTo("before-token-abc");
    assertThat(entity.getAfterToken()).isEqualTo("after-token-xyz");
    assertThat(entity.getExprHash()).isEqualTo("expr-hash-001");
    assertThat(entity.getCommittedAt()).isEqualTo(now);
    assertThat(entity.getError()).isNull();
    assertThat(entity.getStorageKey()).isEqualTo("s3://bucket/key");

    // 验证IdempotentKey转换为String
    assertThat(entity.getIdempotentKey()).isEqualTo("a".repeat(64));

    // 验证BatchStatus转换为String
    assertThat(entity.getStatusCode()).isEqualTo("SUCCEEDED");

    // 验证BatchStats转换
    assertThat(entity.getRecordCount()).isEqualTo(150);
    assertThat(entity.getStats()).isNotNull();
    assertThat(entity.getStats().get("recordCount").asInt()).isEqualTo(150);
  }

  @Test
  @DisplayName("应当正确将TaskRunBatchDO转换为TaskRunBatch")
  void shouldConvertEntityToDomain() {
    // Given: 构造完整的TaskRunBatchDO
    Instant now = Instant.now();
    ObjectNode statsNode = JsonMapperHolder.getObjectMapper().createObjectNode();
    statsNode.put("recordCount", 200);

    TaskRunBatchDO entity = new TaskRunBatchDO();
    entity.setId(1001L);
    entity.setRunId(2001L);
    entity.setTaskId(3001L);
    entity.setSliceId(4001L);
    entity.setPlanId(5001L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setBatchNo(2);
    entity.setPageNo(2);
    entity.setPageSize(50);
    entity.setBeforeToken("token-before");
    entity.setAfterToken("token-after");
    entity.setExprHash("expr-hash-002");
    entity.setIdempotentKey("b".repeat(64));
    entity.setRecordCount(200);
    entity.setStats(statsNode);
    entity.setStatusCode("SUCCEEDED");
    entity.setCommittedAt(now);
    entity.setError(null);
    entity.setStorageKey("s3://bucket/key2");

    // When: 转换为领域对象
    TaskRunBatch batch = converter.toDomain(entity);

    // Then: 验证所有字段正确映射
    assertThat(batch).isNotNull();
    assertThat(batch.getId()).isEqualTo(1001L);
    assertThat(batch.getRunId()).isEqualTo(2001L);
    assertThat(batch.getTaskId()).isEqualTo(3001L);
    assertThat(batch.getSliceId()).isEqualTo(4001L);
    assertThat(batch.getPlanId()).isEqualTo(5001L);
    assertThat(batch.getProvenanceCode()).isEqualTo(ProvenanceCode.PUBMED);
    assertThat(batch.getOperationCode()).isEqualTo("HARVEST");
    assertThat(batch.getBatchNo()).isEqualTo(2);
    assertThat(batch.getPageNo()).isEqualTo(2);
    assertThat(batch.getPageSize()).isEqualTo(50);
    assertThat(batch.getBeforeToken()).isEqualTo("token-before");
    assertThat(batch.getAfterToken()).isEqualTo("token-after");
    assertThat(batch.getExprHash()).isEqualTo("expr-hash-002");
    assertThat(batch.getCommittedAt()).isEqualTo(now);
    assertThat(batch.getError()).isNull();
    assertThat(batch.getStorageKey()).isEqualTo("s3://bucket/key2");

    // 验证String转换为IdempotentKey
    assertThat(batch.getIdempotentKey()).isNotNull();
    assertThat(batch.getIdempotentKey().value()).isEqualTo("b".repeat(64));

    // 验证String转换为BatchStatus
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.SUCCEEDED);

    // 验证recordCount和stats转换为BatchStats
    assertThat(batch.getStats()).isNotNull();
    assertThat(batch.getStats().recordCount()).isEqualTo(200);
  }

  @Test
  @DisplayName("应当支持双向转换的一致性")
  void shouldMaintainConsistencyInRoundTripConversion() {
    // Given: 原始TaskRunBatch
    IdempotentKey key = new IdempotentKey("c".repeat(64));
    BatchStats stats = BatchStats.of(75);

    TaskRunBatch original =
        TaskRunBatch.restore(
            1L,
            2L,
            3L,
            4L,
            5L,
            ProvenanceCode.PUBMED,
            "HARVEST",
            3,
            null,
            null,
            "cursor-token",
            "next-cursor",
            "hash-001",
            key,
            BatchStatus.RUNNING,
            stats,
            null,
            null,
            null);

    // When: Domain → DO → Domain
    TaskRunBatchDO entity = converter.toDO(original);
    TaskRunBatch restored = converter.toDomain(entity);

    // Then: 关键字段应保持一致
    assertThat(restored.getId()).isEqualTo(original.getId());
    assertThat(restored.getRunId()).isEqualTo(original.getRunId());
    assertThat(restored.getBatchNo()).isEqualTo(original.getBatchNo());
    assertThat(restored.getStatus()).isEqualTo(original.getStatus());
    assertThat(restored.getStats().recordCount()).isEqualTo(original.getStats().recordCount());
    assertThat(restored.getIdempotentKey().value()).isEqualTo(original.getIdempotentKey().value());
  }

  @Test
  @DisplayName("应当正确处理所有BatchStatus枚举值的转换")
  void shouldConvertAllBatchStatusValues() {
    // Given & When & Then: 测试所有状态枚举
    assertThat(TaskRunBatchConverter.batchStatusToCode(BatchStatus.RUNNING)).isEqualTo("RUNNING");
    assertThat(TaskRunBatchConverter.batchStatusToCode(BatchStatus.SUCCEEDED))
        .isEqualTo("SUCCEEDED");
    assertThat(TaskRunBatchConverter.batchStatusToCode(BatchStatus.FAILED)).isEqualTo("FAILED");
    assertThat(TaskRunBatchConverter.batchStatusToCode(null)).isNull();

    assertThat(TaskRunBatchConverter.batchStatusFromCode("RUNNING")).isEqualTo(BatchStatus.RUNNING);
    assertThat(TaskRunBatchConverter.batchStatusFromCode("SUCCEEDED"))
        .isEqualTo(BatchStatus.SUCCEEDED);
    assertThat(TaskRunBatchConverter.batchStatusFromCode("FAILED")).isEqualTo(BatchStatus.FAILED);
    assertThat(TaskRunBatchConverter.batchStatusFromCode(null))
        .isEqualTo(BatchStatus.RUNNING); // 默认值
  }

  @Test
  @DisplayName("应当正确处理IdempotentKey与String的双向转换")
  void shouldConvertIdempotentKeyBidirectionally() {
    // Given: IdempotentKey
    IdempotentKey key = new IdempotentKey("d".repeat(64));

    // When: 转换为String
    String keyString = TaskRunBatchConverter.idempotentKeyToString(key);

    // Then: 应正确转换
    assertThat(keyString).isEqualTo("d".repeat(64));

    // When: 转换回IdempotentKey
    IdempotentKey restoredKey = TaskRunBatchConverter.stringToIdempotentKey(keyString);

    // Then: 应正确转换回来
    assertThat(restoredKey).isNotNull();
    assertThat(restoredKey.value()).isEqualTo("d".repeat(64));
  }

  @Test
  @DisplayName("应当正确处理null IdempotentKey")
  void shouldHandleNullIdempotentKey() {
    // Given: null key
    IdempotentKey key = null;

    // When: 转换为String
    String keyString = TaskRunBatchConverter.idempotentKeyToString(key);

    // Then: 应返回null
    assertThat(keyString).isNull();

    // When: null String转换为IdempotentKey
    IdempotentKey restoredKey = TaskRunBatchConverter.stringToIdempotentKey(null);

    // Then: 应返回null
    assertThat(restoredKey).isNull();
  }

  @Test
  @DisplayName("应当正确从recordCount派生BatchStats")
  void shouldDeriveBatchStatsFromRecordCount() {
    // Given: recordCount不为null，stats为null
    Integer recordCount = 250;
    JsonNode statsNode = null;

    // When: 派生BatchStats
    BatchStats stats = TaskRunBatchConverter.deriveStats(recordCount, statsNode);

    // Then: 应从recordCount创建
    assertThat(stats).isNotNull();
    assertThat(stats.recordCount()).isEqualTo(250);
  }

  @Test
  @DisplayName("应当正确从stats JSON节点派生BatchStats")
  void shouldDeriveBatchStatsFromJsonNode() {
    // Given: recordCount为null，stats不为null
    Integer recordCount = null;
    ObjectNode statsNode = JsonMapperHolder.getObjectMapper().createObjectNode();
    statsNode.put("recordCount", 300);

    // When: 派生BatchStats
    BatchStats stats = TaskRunBatchConverter.deriveStats(recordCount, statsNode);

    // Then: 应从stats JSON创建
    assertThat(stats).isNotNull();
    assertThat(stats.recordCount()).isEqualTo(300);
  }

  @Test
  @DisplayName("应当优先使用recordCount而非stats JSON")
  void shouldPrioritizeRecordCountOverStatsJson() {
    // Given: recordCount和stats都不为null，值不同
    Integer recordCount = 100;
    ObjectNode statsNode = JsonMapperHolder.getObjectMapper().createObjectNode();
    statsNode.put("recordCount", 200);

    // When: 派生BatchStats
    BatchStats stats = TaskRunBatchConverter.deriveStats(recordCount, statsNode);

    // Then: 应使用recordCount
    assertThat(stats).isNotNull();
    assertThat(stats.recordCount()).isEqualTo(100);
  }

  @Test
  @DisplayName("应当在recordCount和stats都为null时返回默认值0")
  void shouldReturnZeroWhenBothRecordCountAndStatsAreNull() {
    // Given: recordCount和stats都为null
    Integer recordCount = null;
    JsonNode statsNode = null;

    // When: 派生BatchStats
    BatchStats stats = TaskRunBatchConverter.deriveStats(recordCount, statsNode);

    // Then: 应返回默认值0
    assertThat(stats).isNotNull();
    assertThat(stats.recordCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("应当正确处理BatchStats转换为recordCount")
  void shouldExtractRecordCountFromBatchStats() {
    // Given: BatchStats
    BatchStats stats = BatchStats.of(500);

    // When: 提取recordCount
    Integer recordCount = TaskRunBatchConverter.statsToRecordCount(stats);

    // Then: 应正确提取
    assertThat(recordCount).isEqualTo(500);
  }

  @Test
  @DisplayName("应当正确处理null BatchStats转换为recordCount")
  void shouldReturnNullRecordCountForNullBatchStats() {
    // Given: null BatchStats
    BatchStats stats = null;

    // When: 提取recordCount
    Integer recordCount = TaskRunBatchConverter.statsToRecordCount(stats);

    // Then: 应返回null
    assertThat(recordCount).isNull();
  }

  @Test
  @DisplayName("应当正确将BatchStats转换为JSON")
  void shouldConvertBatchStatsToJson() {
    // Given: BatchStats
    BatchStats stats = BatchStats.of(350);

    // When: 转换为JSON
    JsonNode statsJson = TaskRunBatchConverter.statsToJson(stats);

    // Then: 应正确转换
    assertThat(statsJson).isNotNull();
    assertThat(statsJson.get("recordCount").asInt()).isEqualTo(350);
  }

  @Test
  @DisplayName("应当正确处理null BatchStats转换为JSON")
  void shouldReturnNullJsonForNullBatchStats() {
    // Given: null BatchStats
    BatchStats stats = null;

    // When: 转换为JSON
    JsonNode statsJson = TaskRunBatchConverter.statsToJson(stats);

    // Then: 应返回null
    assertThat(statsJson).isNull();
  }

  @Test
  @DisplayName("应当正确处理空batchNo，默认为0")
  void shouldHandleNullBatchNoAsZero() {
    // Given: batchNo为null的DO
    TaskRunBatchDO entity = new TaskRunBatchDO();
    entity.setId(1L);
    entity.setRunId(2L);
    entity.setTaskId(3L);
    entity.setSliceId(4L);
    entity.setPlanId(5L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setBatchNo(null);
    entity.setExprHash("hash-001");
    entity.setIdempotentKey("e".repeat(64));
    entity.setStatusCode("RUNNING");

    // When: 转换为领域对象
    TaskRunBatch batch = converter.toDomain(entity);

    // Then: batchNo应为0
    assertThat(batch.getBatchNo()).isEqualTo(0);
  }

  @Test
  @DisplayName("应当正确处理null DO转换")
  void shouldHandleNullEntity() {
    // Given: null DO
    TaskRunBatchDO entity = null;

    // When: 转换为领域对象
    TaskRunBatch batch = converter.toDomain(entity);

    // Then: 应返回null
    assertThat(batch).isNull();
  }

  @Test
  @DisplayName("应当正确处理包含error的TaskRunBatch")
  void shouldHandleTaskRunBatchWithError() {
    // Given: 包含error的TaskRunBatch
    Instant now = Instant.now();
    IdempotentKey key = new IdempotentKey("f".repeat(64));
    TaskRunBatch batch =
        TaskRunBatch.restore(
            1L,
            2L,
            3L,
            4L,
            5L,
            ProvenanceCode.PUBMED,
            "HARVEST",
            1,
            1,
            100,
            "token-before",
            "token-after",
            "hash-001",
            key,
            BatchStatus.FAILED,
            BatchStats.of(0),
            now,
            "API rate limit exceeded",
            null);

    // When: 转换为DO
    TaskRunBatchDO entity = converter.toDO(batch);

    // Then: error应正确映射
    assertThat(entity.getError()).isEqualTo("API rate limit exceeded");
    assertThat(entity.getStatusCode()).isEqualTo("FAILED");
  }

  @Test
  @DisplayName("应当正确处理基于游标的分页（pageNo为null）")
  void shouldHandleCursorBasedPagination() {
    // Given: 游标分页的TaskRunBatch（pageNo为null）
    IdempotentKey key = new IdempotentKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    TaskRunBatch batch =
        TaskRunBatch.restore(
            1L,
            2L,
            3L,
            4L,
            5L,
            ProvenanceCode.PUBMED,
            "HARVEST",
            1,
            null, // pageNo为null
            null, // pageSize为null
            "cursor-abc",
            "cursor-xyz",
            "hash-001",
            key,
            BatchStatus.SUCCEEDED,
            BatchStats.of(50),
            Instant.now(),
            null,
            null);

    // When: 转换为DO
    TaskRunBatchDO entity = converter.toDO(batch);

    // Then: 游标字段应正确映射
    assertThat(entity.getPageNo()).isNull();
    assertThat(entity.getPageSize()).isNull();
    assertThat(entity.getBeforeToken()).isEqualTo("cursor-abc");
    assertThat(entity.getAfterToken()).isEqualTo("cursor-xyz");
  }

  @Test
  @DisplayName("应当正确处理基于页码的分页（beforeToken为null）")
  void shouldHandlePageBasedPagination() {
    // Given: 页码分页的TaskRunBatch（beforeToken为null）
    IdempotentKey key = new IdempotentKey("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210");
    TaskRunBatch batch =
        TaskRunBatch.restore(
            1L,
            2L,
            3L,
            4L,
            5L,
            ProvenanceCode.PUBMED,
            "HARVEST",
            5,
            5,
            20,
            null, // beforeToken为null
            null, // afterToken为null
            "hash-001",
            key,
            BatchStatus.SUCCEEDED,
            BatchStats.of(20),
            Instant.now(),
            null,
            null);

    // When: 转换为DO
    TaskRunBatchDO entity = converter.toDO(batch);

    // Then: 页码字段应正确映射
    assertThat(entity.getPageNo()).isEqualTo(5);
    assertThat(entity.getPageSize()).isEqualTo(20);
    assertThat(entity.getBeforeToken()).isNull();
    assertThat(entity.getAfterToken()).isNull();
  }
}
