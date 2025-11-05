package com.patra.ingest.infra.persistence.repository;

import static org.assertj.core.api.Assertions.*;

import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.batch.BatchStats;
import com.patra.ingest.domain.model.vo.shared.IdempotentKey;
import com.patra.ingest.infra.persistence.mapper.TaskRunBatchMapper;
import com.patra.ingest.integration.BaseIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * TaskRunBatchRepositoryMpImpl 集成测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>使用 TestContainers 提供真实的 MySQL 数据库环境
 *   <li>使用 @Transactional 确保测试隔离和自动回滚
 *   <li>覆盖单个保存、批量保存、查询等关键场景
 * </ul>
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>基本 CRUD：save (insert/update)、saveAll
 *   <li>自定义查询：findByRunId、findLastSucceededBatchId
 *   <li>幂等性：幂等键唯一性约束
 *   <li>批量操作：批量插入和更新
 *   <li>边界情况：空列表、null 参数、不存在的 ID
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("TaskRunBatchRepository 集成测试")
class TaskRunBatchRepositoryMpImplTest extends BaseIntegrationTest {

  @Autowired private TaskRunBatchRepositoryMpImpl repository;

  @Autowired private TaskRunBatchMapper mapper;

  private static final Long TEST_RUN_ID = 1000L;
  private static final Long TEST_TASK_ID = 100L;
  private static final Long TEST_SLICE_ID = 10L;
  private static final Long TEST_PLAN_ID = 1L;
  private static final String TEST_PROVENANCE_CODE = "PUBMED";
  private static final String TEST_OPERATION_CODE = "LIST";
  private static final String TEST_EXPR_HASH = "hash123";

  @BeforeEach
  void setUp() {
    // 清理测试数据
    mapper.delete(null);
  }

  // ==================== 辅助方法 ====================

  private TaskRunBatch createBatch(int batchNo, Integer pageNo) {
    return new TaskRunBatch(
        null, // id
        TEST_RUN_ID,
        TEST_TASK_ID,
        TEST_SLICE_ID,
        TEST_PLAN_ID,
        TEST_PROVENANCE_CODE,
        TEST_OPERATION_CODE,
        batchNo,
        pageNo,
        100, // pageSize
        null, // beforeToken
        TEST_EXPR_HASH,
        new IdempotentKey("idem-" + TEST_RUN_ID + "-" + batchNo));
  }

  private TaskRunBatch createTokenBasedBatch(int batchNo, String beforeToken) {
    return new TaskRunBatch(
        null, // id
        TEST_RUN_ID,
        TEST_TASK_ID,
        TEST_SLICE_ID,
        TEST_PLAN_ID,
        TEST_PROVENANCE_CODE,
        TEST_OPERATION_CODE,
        batchNo,
        null, // pageNo - token-based pagination
        null, // pageSize - token-based pagination
        beforeToken,
        TEST_EXPR_HASH,
        new IdempotentKey("idem-token-" + TEST_RUN_ID + "-" + beforeToken));
  }

  // ==================== save 操作 ====================

  @Test
  @DisplayName("应该插入新批次记录")
  void shouldInsertNewBatch() {
    // Arrange
    TaskRunBatch batch = createBatch(1, 1);

    // Act
    repository.save(batch);

    // Assert
    assertThat(batch.getId()).isNotNull();
    assertThat(batch.getRunId()).isEqualTo(TEST_RUN_ID);
    assertThat(batch.getBatchNo()).isEqualTo(1);
    assertThat(batch.getPageNo()).isEqualTo(1);
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.RUNNING);
  }

  @Test
  @DisplayName("应该更新已存在的批次记录")
  void shouldUpdateExistingBatch() {
    // Arrange
    TaskRunBatch batch = createBatch(1, 1);
    repository.save(batch);

    // 模拟批次完成
    Instant now = Instant.now();
    TaskRunBatch updated =
        TaskRunBatch.restore(
            batch.getId(),
            batch.getRunId(),
            batch.getTaskId(),
            batch.getSliceId(),
            batch.getPlanId(),
            batch.getProvenanceCode(),
            batch.getOperationCode(),
            batch.getBatchNo(),
            batch.getPageNo(),
            batch.getPageSize(),
            batch.getBeforeToken(),
            "nextToken123", // afterToken
            batch.getExprHash(),
            batch.getIdempotentKey(),
            BatchStatus.SUCCEEDED,
            BatchStats.of(50),
            now,
            null,
            null);

    // Act
    repository.save(updated);

    // Assert
    List<TaskRunBatch> batches = repository.findByRunId(TEST_RUN_ID);
    assertThat(batches).hasSize(1);
    TaskRunBatch persisted = batches.get(0);
    assertThat(persisted.getId()).isEqualTo(batch.getId());
    assertThat(persisted.getStatus()).isEqualTo(BatchStatus.SUCCEEDED);
    assertThat(persisted.getStats().recordCount()).isEqualTo(50);
    assertThat(persisted.getAfterToken()).isEqualTo("nextToken123");
  }

  @Test
  @DisplayName("应该保存基于令牌的分页批次")
  void shouldSaveTokenBasedBatch() {
    // Arrange
    TaskRunBatch batch = createTokenBasedBatch(1, "token_abc");

    // Act
    repository.save(batch);

    // Assert
    assertThat(batch.getId()).isNotNull();
    assertThat(batch.getPageNo()).isNull();
    assertThat(batch.getPageSize()).isNull();
    assertThat(batch.getBeforeToken()).isEqualTo("token_abc");
  }

  // ==================== saveAll 批量操作 ====================

  @Test
  @DisplayName("应该批量保存多个批次")
  void shouldSaveAllBatches() {
    // Arrange
    List<TaskRunBatch> batches = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      batches.add(createBatch(i, i));
    }

    // Act
    repository.saveAll(batches);

    // Assert
    List<TaskRunBatch> saved = repository.findByRunId(TEST_RUN_ID);
    assertThat(saved).hasSize(10);
    assertThat(saved).extracting(TaskRunBatch::getBatchNo).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  }

  @Test
  @DisplayName("应该处理空列表的批量保存")
  void shouldHandleEmptyListInSaveAll() {
    // Act & Assert - 不应该抛出异常
    assertThatCode(() -> repository.saveAll(List.of())).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("应该批量更新已存在的批次")
  void shouldBatchUpdateExistingBatches() {
    // Arrange - 先插入
    List<TaskRunBatch> batches = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      batches.add(createBatch(i, i));
    }
    repository.saveAll(batches);

    // 修改状态为成功
    List<TaskRunBatch> updated = new ArrayList<>();
    for (TaskRunBatch batch : repository.findByRunId(TEST_RUN_ID)) {
      TaskRunBatch succeededBatch =
          TaskRunBatch.restore(
              batch.getId(),
              batch.getRunId(),
              batch.getTaskId(),
              batch.getSliceId(),
              batch.getPlanId(),
              batch.getProvenanceCode(),
              batch.getOperationCode(),
              batch.getBatchNo(),
              batch.getPageNo(),
              batch.getPageSize(),
              batch.getBeforeToken(),
              null,
              batch.getExprHash(),
              batch.getIdempotentKey(),
              BatchStatus.SUCCEEDED,
              BatchStats.of(100),
              Instant.now(),
              null,
              null);
      updated.add(succeededBatch);
    }

    // Act
    repository.saveAll(updated);

    // Assert
    List<TaskRunBatch> persisted = repository.findByRunId(TEST_RUN_ID);
    assertThat(persisted).hasSize(5);
    assertThat(persisted).allMatch(b -> b.getStatus() == BatchStatus.SUCCEEDED);
    assertThat(persisted).allMatch(b -> b.getStats().recordCount() == 100);
  }

  // ==================== findByRunId 查询 ====================

  @Test
  @DisplayName("应该查找指定执行的所有批次")
  void shouldFindByRunId() {
    // Arrange
    repository.save(createBatch(1, 1));
    repository.save(createBatch(2, 2));
    repository.save(createBatch(3, 3));

    // 创建另一个 runId 的批次(验证隔离)
    TaskRunBatch otherRunBatch =
        new TaskRunBatch(
            null,
            2000L, // 不同的 runId
            TEST_TASK_ID,
            TEST_SLICE_ID,
            TEST_PLAN_ID,
            TEST_PROVENANCE_CODE,
            TEST_OPERATION_CODE,
            1,
            1,
            100,
            null,
            TEST_EXPR_HASH,
            new IdempotentKey("idem-other"));
    repository.save(otherRunBatch);

    // Act
    List<TaskRunBatch> batches = repository.findByRunId(TEST_RUN_ID);

    // Assert
    assertThat(batches).hasSize(3);
    assertThat(batches).allMatch(b -> b.getRunId().equals(TEST_RUN_ID));
    assertThat(batches).extracting(TaskRunBatch::getBatchNo).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  @DisplayName("应该在执行没有批次时返回空列表")
  void shouldReturnEmptyListWhenNoBatchesFound() {
    // Act
    List<TaskRunBatch> batches = repository.findByRunId(99999L);

    // Assert
    assertThat(batches).isEmpty();
  }

  // ==================== findLastSucceededBatchId 查询 ====================

  @Test
  @DisplayName("应该查找最后一个成功批次的 ID")
  void shouldFindLastSucceededBatchId() {
    // Arrange - 创建多个批次,部分成功
    Instant now = Instant.now();

    // 批次 1: 成功
    TaskRunBatch batch1 = createBatch(1, 1);
    repository.save(batch1);
    TaskRunBatch succeeded1 =
        TaskRunBatch.restore(
            batch1.getId(),
            batch1.getRunId(),
            batch1.getTaskId(),
            batch1.getSliceId(),
            batch1.getPlanId(),
            batch1.getProvenanceCode(),
            batch1.getOperationCode(),
            batch1.getBatchNo(),
            batch1.getPageNo(),
            batch1.getPageSize(),
            batch1.getBeforeToken(),
            null,
            batch1.getExprHash(),
            batch1.getIdempotentKey(),
            BatchStatus.SUCCEEDED,
            BatchStats.of(100),
            now,
            null,
            null);
    repository.save(succeeded1);

    // 批次 2: 失败
    TaskRunBatch batch2 = createBatch(2, 2);
    repository.save(batch2);
    TaskRunBatch failed2 =
        TaskRunBatch.restore(
            batch2.getId(),
            batch2.getRunId(),
            batch2.getTaskId(),
            batch2.getSliceId(),
            batch2.getPlanId(),
            batch2.getProvenanceCode(),
            batch2.getOperationCode(),
            batch2.getBatchNo(),
            batch2.getPageNo(),
            batch2.getPageSize(),
            batch2.getBeforeToken(),
            null,
            batch2.getExprHash(),
            batch2.getIdempotentKey(),
            BatchStatus.FAILED,
            BatchStats.of(0),
            now,
            "Test error",
            null);
    repository.save(failed2);

    // 批次 3: 成功 (最新)
    TaskRunBatch batch3 = createBatch(3, 3);
    repository.save(batch3);
    TaskRunBatch succeeded3 =
        TaskRunBatch.restore(
            batch3.getId(),
            batch3.getRunId(),
            batch3.getTaskId(),
            batch3.getSliceId(),
            batch3.getPlanId(),
            batch3.getProvenanceCode(),
            batch3.getOperationCode(),
            batch3.getBatchNo(),
            batch3.getPageNo(),
            batch3.getPageSize(),
            batch3.getBeforeToken(),
            null,
            batch3.getExprHash(),
            batch3.getIdempotentKey(),
            BatchStatus.SUCCEEDED,
            BatchStats.of(50),
            now.plusSeconds(10),
            null,
            null);
    repository.save(succeeded3);

    // Act
    Optional<Long> lastSucceededBatchId = repository.findLastSucceededBatchId(TEST_RUN_ID);

    // Assert
    assertThat(lastSucceededBatchId).isPresent();
    assertThat(lastSucceededBatchId.get()).isEqualTo(batch3.getId());
  }

  @Test
  @DisplayName("应该在没有成功批次时返回空 Optional")
  void shouldReturnEmptyWhenNoSucceededBatches() {
    // Arrange - 只有失败批次
    TaskRunBatch batch = createBatch(1, 1);
    repository.save(batch);
    TaskRunBatch failed =
        TaskRunBatch.restore(
            batch.getId(),
            batch.getRunId(),
            batch.getTaskId(),
            batch.getSliceId(),
            batch.getPlanId(),
            batch.getProvenanceCode(),
            batch.getOperationCode(),
            batch.getBatchNo(),
            batch.getPageNo(),
            batch.getPageSize(),
            batch.getBeforeToken(),
            null,
            batch.getExprHash(),
            batch.getIdempotentKey(),
            BatchStatus.FAILED,
            BatchStats.of(0),
            Instant.now(),
            "Error",
            null);
    repository.save(failed);

    // Act
    Optional<Long> lastSucceededBatchId = repository.findLastSucceededBatchId(TEST_RUN_ID);

    // Assert
    assertThat(lastSucceededBatchId).isEmpty();
  }

  @Test
  @DisplayName("应该在执行没有批次时返回空 Optional")
  void shouldReturnEmptyWhenRunHasNoBatches() {
    // Act
    Optional<Long> lastSucceededBatchId = repository.findLastSucceededBatchId(99999L);

    // Assert
    assertThat(lastSucceededBatchId).isEmpty();
  }

  // ==================== 复杂场景 ====================

  @Test
  @DisplayName("应该处理多执行多批次的复杂场景")
  void shouldHandleMultipleRunsWithMultipleBatches() {
    // Arrange - 两个执行,每个执行 5 个批次
    Long runId1 = 1001L;
    Long runId2 = 1002L;

    for (int i = 1; i <= 5; i++) {
      TaskRunBatch batch1 =
          new TaskRunBatch(
              null,
              runId1,
              TEST_TASK_ID,
              TEST_SLICE_ID,
              TEST_PLAN_ID,
              TEST_PROVENANCE_CODE,
              TEST_OPERATION_CODE,
              i,
              i,
              100,
              null,
              TEST_EXPR_HASH,
              new IdempotentKey("idem-run1-" + i));
      repository.save(batch1);

      TaskRunBatch batch2 =
          new TaskRunBatch(
              null,
              runId2,
              TEST_TASK_ID,
              TEST_SLICE_ID,
              TEST_PLAN_ID,
              "EPMC",
              "SEARCH",
              i,
              i,
              100,
              null,
              "hash456",
              new IdempotentKey("idem-run2-" + i));
      repository.save(batch2);
    }

    // Act & Assert - 执行 1
    List<TaskRunBatch> run1Batches = repository.findByRunId(runId1);
    assertThat(run1Batches).hasSize(5);
    assertThat(run1Batches).allMatch(b -> b.getRunId().equals(runId1));
    assertThat(run1Batches).allMatch(b -> b.getProvenanceCode().equals(TEST_PROVENANCE_CODE));

    // Act & Assert - 执行 2
    List<TaskRunBatch> run2Batches = repository.findByRunId(runId2);
    assertThat(run2Batches).hasSize(5);
    assertThat(run2Batches).allMatch(b -> b.getRunId().equals(runId2));
    assertThat(run2Batches).allMatch(b -> b.getProvenanceCode().equals("EPMC"));
  }

  @Test
  @DisplayName("应该处理混合分页类型的批次")
  void shouldHandleMixedPaginationTypes() {
    // Arrange - 页码分页 + 令牌分页
    repository.save(createBatch(1, 1)); // 页码
    repository.save(createBatch(2, 2)); // 页码
    repository.save(createTokenBasedBatch(3, "token_a")); // 令牌
    repository.save(createTokenBasedBatch(4, "token_b")); // 令牌

    // Act
    List<TaskRunBatch> batches = repository.findByRunId(TEST_RUN_ID);

    // Assert
    assertThat(batches).hasSize(4);

    // 验证页码分页批次
    List<TaskRunBatch> pageBatches =
        batches.stream().filter(b -> b.getPageNo() != null).toList();
    assertThat(pageBatches).hasSize(2);
    assertThat(pageBatches).allMatch(b -> b.getPageSize() != null);

    // 验证令牌分页批次
    List<TaskRunBatch> tokenBatches =
        batches.stream().filter(b -> b.getBeforeToken() != null).toList();
    assertThat(tokenBatches).hasSize(2);
    assertThat(tokenBatches).allMatch(b -> b.getPageNo() == null);
    assertThat(tokenBatches).allMatch(b -> b.getPageSize() == null);
  }

  @Test
  @DisplayName("应该正确处理批次状态转换")
  void shouldHandleBatchStatusTransitions() {
    // Arrange
    TaskRunBatch batch = createBatch(1, 1);
    repository.save(batch);

    // Act & Assert - RUNNING → SUCCEEDED
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.RUNNING);

    Instant now = Instant.now();
    TaskRunBatch succeeded =
        TaskRunBatch.restore(
            batch.getId(),
            batch.getRunId(),
            batch.getTaskId(),
            batch.getSliceId(),
            batch.getPlanId(),
            batch.getProvenanceCode(),
            batch.getOperationCode(),
            batch.getBatchNo(),
            batch.getPageNo(),
            batch.getPageSize(),
            batch.getBeforeToken(),
            "nextToken",
            batch.getExprHash(),
            batch.getIdempotentKey(),
            BatchStatus.SUCCEEDED,
            BatchStats.of(200),
            now,
            null,
            null);
    repository.save(succeeded);

    List<TaskRunBatch> persisted = repository.findByRunId(TEST_RUN_ID);
    assertThat(persisted).hasSize(1);
    assertThat(persisted.get(0).getStatus()).isEqualTo(BatchStatus.SUCCEEDED);
    assertThat(persisted.get(0).getStats().recordCount()).isEqualTo(200);
    assertThat(persisted.get(0).getAfterToken()).isEqualTo("nextToken");
  }

  @Test
  @DisplayName("应该验证幂等键的唯一性")
  void shouldEnforceIdempotentKeyUniqueness() {
    // Arrange
    TaskRunBatch batch1 =
        new TaskRunBatch(
            null,
            TEST_RUN_ID,
            TEST_TASK_ID,
            TEST_SLICE_ID,
            TEST_PLAN_ID,
            TEST_PROVENANCE_CODE,
            TEST_OPERATION_CODE,
            1,
            1,
            100,
            null,
            TEST_EXPR_HASH,
            new IdempotentKey("duplicate-key"));

    TaskRunBatch batch2 =
        new TaskRunBatch(
            null,
            TEST_RUN_ID,
            TEST_TASK_ID,
            TEST_SLICE_ID,
            TEST_PLAN_ID,
            TEST_PROVENANCE_CODE,
            TEST_OPERATION_CODE,
            2,
            2,
            100,
            null,
            TEST_EXPR_HASH,
            new IdempotentKey("duplicate-key")); // 相同的幂等键

    // Act & Assert
    repository.save(batch1);
    assertThatThrownBy(() -> repository.save(batch2))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .hasMessageContaining("Duplicate entry");
  }
}
