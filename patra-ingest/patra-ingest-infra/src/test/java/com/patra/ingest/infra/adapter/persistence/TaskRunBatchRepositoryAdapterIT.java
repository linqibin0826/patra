package com.patra.ingest.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.shared.IdempotentKey;
import com.patra.ingest.infra.adapter.persistence.dao.PlanDao;
import com.patra.ingest.infra.adapter.persistence.dao.PlanSliceDao;
import com.patra.ingest.infra.adapter.persistence.dao.ScheduleInstanceDao;
import com.patra.ingest.infra.adapter.persistence.dao.TaskDao;
import com.patra.ingest.infra.adapter.persistence.dao.TaskRunBatchDao;
import com.patra.ingest.infra.adapter.persistence.dao.TaskRunDao;
import com.patra.ingest.infra.adapter.persistence.entity.PlanEntity;
import com.patra.ingest.infra.adapter.persistence.entity.PlanSliceEntity;
import com.patra.ingest.infra.adapter.persistence.entity.ScheduleInstanceEntity;
import com.patra.ingest.infra.adapter.persistence.entity.TaskEntity;
import com.patra.ingest.infra.adapter.persistence.entity.TaskRunBatchEntity;
import com.patra.ingest.infra.adapter.persistence.entity.TaskRunEntity;
import com.patra.ingest.infra.config.IngestMySQLContainerInitializer;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// TaskRunBatchRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + MySQL 8 测试任务执行批次持久化。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 MySQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：save、saveAll、findByRunId、findLastSucceededBatchId
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = IngestMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  TaskRunBatchRepositoryAdapter.class,
  JacksonAutoConfiguration.class,
  JpaAuditingConfig.class
})
@ComponentScan(basePackages = "com.patra.ingest.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("TaskRunBatchRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class TaskRunBatchRepositoryAdapterIT {

  @Autowired private TaskRunBatchRepositoryAdapter repository;

  @Autowired private TaskRunBatchDao batchDao;
  @Autowired private TaskRunDao runDao;
  @Autowired private TaskDao taskDao;
  @Autowired private PlanSliceDao planSliceDao;
  @Autowired private PlanDao planDao;
  @Autowired private ScheduleInstanceDao scheduleInstanceDao;
  @Autowired private ObjectMapper objectMapper;

  private static final String TEST_PROVENANCE_CODE = "PUBMED";
  private static final String TEST_OPERATION_CODE = "HARVEST";
  private static final String TEST_EXPR_HASH = "expr-hash-001";

  /// 测试用的有效幂等键（64 位 SHA256 十六进制字符串）
  private static final String VALID_IDEM_KEY_1 =
      "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
  private static final String VALID_IDEM_KEY_2 =
      "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3";
  private static final String VALID_IDEM_KEY_3 =
      "c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";

  private Long testScheduleInstanceId;
  private Long testPlanId;
  private Long testSliceId;
  private Long testTaskId;
  private Long testRunId;

  @BeforeEach
  void setUp() {
    // 清理现有数据（按外键依赖顺序）
    batchDao.deleteAllInBatch();
    runDao.deleteAllInBatch();
    taskDao.deleteAllInBatch();
    planSliceDao.deleteAllInBatch();
    planDao.deleteAllInBatch();
    scheduleInstanceDao.deleteAllInBatch();

    // 创建依赖数据
    testScheduleInstanceId = insertScheduleInstance();
    testPlanId = insertPlan();
    testSliceId = insertPlanSlice();
    testTaskId = insertTask();
    testRunId = insertTaskRun();
  }

  @Nested
  @DisplayName("save 操作")
  class SaveTests {

    @Test
    @DisplayName("应保存新批次并返回带 ID 的实体")
    void shouldSaveNewBatchAndReturnWithId() {
      // Given
      TaskRunBatch batch = createTestBatch(1, VALID_IDEM_KEY_1);

      // When
      TaskRunBatch saved = repository.save(batch);

      // Then
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getRunId()).isEqualTo(testRunId);
      assertThat(saved.getBatchNo()).isEqualTo(1);
      assertThat(saved.getIdempotentKey().value()).isEqualTo(VALID_IDEM_KEY_1);
    }

    @Test
    @DisplayName("应更新已有批次")
    void shouldUpdateExistingBatch() {
      // Given：先插入一个批次
      TaskRunBatchEntity existingEntity = createTestBatchEntity(1, VALID_IDEM_KEY_1);
      existingEntity.setStatusCode(BatchStatus.RUNNING.getCode());
      batchDao.saveAndFlush(existingEntity);

      // 创建领域对象用于更新
      TaskRunBatch batchToUpdate =
          TaskRunBatch.restore(
              existingEntity.getId(),
              testRunId,
              testTaskId,
              testSliceId,
              testPlanId,
              ProvenanceCode.parse(TEST_PROVENANCE_CODE),
              TEST_OPERATION_CODE,
              1,
              null,
              100,
              null,
              "next-token",
              TEST_EXPR_HASH,
              new IdempotentKey(VALID_IDEM_KEY_1),
              BatchStatus.SUCCEEDED,
              null,
              Instant.now(),
              null,
              null);

      // When
      TaskRunBatch updated = repository.save(batchToUpdate);

      // Then
      TaskRunBatchEntity fromDb = batchDao.findById(existingEntity.getId()).orElse(null);
      assertThat(fromDb).isNotNull();
      assertThat(fromDb.getStatusCode()).isEqualTo(BatchStatus.SUCCEEDED.getCode());
      assertThat(fromDb.getAfterToken()).isEqualTo("next-token");
    }
  }

  @Nested
  @DisplayName("saveAll 操作")
  class SaveAllTests {

    @Test
    @DisplayName("应处理空列表而不抛异常")
    void shouldHandleEmptyList() {
      // When & Then
      repository.saveAll(List.of());
      // 不抛异常即为成功
    }

    @Test
    @DisplayName("应批量插入所有新批次")
    void shouldBatchInsertAllNewBatches() {
      // Given
      List<TaskRunBatch> batches =
          List.of(
              createTestBatch(1, VALID_IDEM_KEY_1),
              createTestBatch(2, VALID_IDEM_KEY_2),
              createTestBatch(3, VALID_IDEM_KEY_3));

      // When
      repository.saveAll(batches);

      // Then
      List<TaskRunBatchEntity> fromDb = batchDao.findByRunId(testRunId);
      assertThat(fromDb).hasSize(3);
    }

    @Test
    @DisplayName("应批量更新所有已有批次")
    void shouldBatchUpdateAllExistingBatches() {
      // Given：先插入批次
      TaskRunBatchEntity batch1 = createTestBatchEntity(1, VALID_IDEM_KEY_1);
      TaskRunBatchEntity batch2 = createTestBatchEntity(2, VALID_IDEM_KEY_2);
      batchDao.saveAndFlush(batch1);
      batchDao.saveAndFlush(batch2);

      // 创建更新对象
      List<TaskRunBatch> toUpdate =
          List.of(
              createTestBatchWithId(batch1.getId(), 1, VALID_IDEM_KEY_1, BatchStatus.SUCCEEDED),
              createTestBatchWithId(batch2.getId(), 2, VALID_IDEM_KEY_2, BatchStatus.FAILED));

      // When
      repository.saveAll(toUpdate);

      // Then
      TaskRunBatchEntity updated1 = batchDao.findById(batch1.getId()).orElse(null);
      TaskRunBatchEntity updated2 = batchDao.findById(batch2.getId()).orElse(null);
      assertThat(updated1).isNotNull();
      assertThat(updated2).isNotNull();
      assertThat(updated1.getStatusCode()).isEqualTo(BatchStatus.SUCCEEDED.getCode());
      assertThat(updated2.getStatusCode()).isEqualTo(BatchStatus.FAILED.getCode());
    }

    @Test
    @DisplayName("应混合处理新增和更新")
    void shouldHandleMixedInsertAndUpdate() {
      // Given：先插入一个批次
      TaskRunBatchEntity existing = createTestBatchEntity(1, VALID_IDEM_KEY_1);
      batchDao.saveAndFlush(existing);

      // 准备混合列表：一个更新 + 一个新增
      List<TaskRunBatch> mixed =
          List.of(
              createTestBatchWithId(existing.getId(), 1, VALID_IDEM_KEY_1, BatchStatus.SUCCEEDED),
              createTestBatch(2, VALID_IDEM_KEY_2));

      // When
      repository.saveAll(mixed);

      // Then
      List<TaskRunBatchEntity> fromDb = batchDao.findByRunId(testRunId);
      assertThat(fromDb).hasSize(2);

      TaskRunBatchEntity updated = batchDao.findById(existing.getId()).orElse(null);
      assertThat(updated).isNotNull();
      assertThat(updated.getStatusCode()).isEqualTo(BatchStatus.SUCCEEDED.getCode());
    }
  }

  @Nested
  @DisplayName("findByRunId 操作")
  class FindByRunIdTests {

    @Test
    @DisplayName("应按 runId 查询所有批次")
    void shouldFindAllBatchesByRunId() {
      // Given
      batchDao.saveAndFlush(createTestBatchEntity(1, VALID_IDEM_KEY_1));
      batchDao.saveAndFlush(createTestBatchEntity(2, VALID_IDEM_KEY_2));
      batchDao.saveAndFlush(createTestBatchEntity(3, VALID_IDEM_KEY_3));

      // When
      List<TaskRunBatch> result = repository.findByRunId(testRunId);

      // Then
      assertThat(result).hasSize(3);
      assertThat(result).extracting(TaskRunBatch::getBatchNo).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("应在无匹配时返回空列表")
    void shouldReturnEmptyListWhenNoMatch() {
      // When
      List<TaskRunBatch> result = repository.findByRunId(999999L);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findLastSucceededBatchId 操作")
  class FindLastSucceededBatchIdTests {

    @Test
    @DisplayName("应返回最后成功批次的 ID")
    void shouldReturnLastSucceededBatchId() {
      // Given：插入多个批次，有不同状态
      TaskRunBatchEntity batch1 = createTestBatchEntity(1, VALID_IDEM_KEY_1);
      batch1.setStatusCode(BatchStatus.SUCCEEDED.getCode());
      batchDao.saveAndFlush(batch1);

      TaskRunBatchEntity batch2 = createTestBatchEntity(2, VALID_IDEM_KEY_2);
      batch2.setStatusCode(BatchStatus.SUCCEEDED.getCode());
      batchDao.saveAndFlush(batch2);

      TaskRunBatchEntity batch3 = createTestBatchEntity(3, VALID_IDEM_KEY_3);
      batch3.setStatusCode(BatchStatus.FAILED.getCode());
      batchDao.saveAndFlush(batch3);

      // When
      Optional<Long> result = repository.findLastSucceededBatchId(testRunId);

      // Then：应返回最后一个 SUCCEEDED 批次的 ID（按 ID 排序，batch2 应该是最大的成功批次）
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(batch2.getId());
    }

    @Test
    @DisplayName("应在无成功批次时返回空 Optional")
    void shouldReturnEmptyWhenNoSucceededBatch() {
      // Given：只有失败的批次
      TaskRunBatchEntity batch1 = createTestBatchEntity(1, VALID_IDEM_KEY_1);
      batch1.setStatusCode(BatchStatus.FAILED.getCode());
      batchDao.saveAndFlush(batch1);

      // When
      Optional<Long> result = repository.findLastSucceededBatchId(testRunId);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应在无任何批次时返回空 Optional")
    void shouldReturnEmptyWhenNoBatches() {
      // When
      Optional<Long> result = repository.findLastSucceededBatchId(testRunId);

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ==================== 辅助方法 ====================

  private Long insertScheduleInstance() {
    ScheduleInstanceEntity instance = new ScheduleInstanceEntity();
    instance.setId(SnowflakeIdGenerator.getId());
    instance.setSchedulerCode("XXL");
    instance.setTriggerTypeCode("SCHEDULE");
    instance.setTriggeredAt(Instant.now());
    instance.setProvenanceCode(TEST_PROVENANCE_CODE);
    scheduleInstanceDao.saveAndFlush(instance);
    return instance.getId();
  }

  private Long insertPlan() {
    PlanEntity plan = new PlanEntity();
    plan.setId(SnowflakeIdGenerator.getId());
    plan.setScheduleInstanceId(testScheduleInstanceId);
    plan.setPlanKey("PUBMED-HARVEST-2025-01-01");
    plan.setProvenanceCode(TEST_PROVENANCE_CODE);
    plan.setOperationCode(TEST_OPERATION_CODE);
    plan.setSliceStrategyCode("SINGLE");
    plan.setExprProtoHash("test-expr-hash-001");
    plan.setWindowSpec(createSingleWindowSpec());
    plan.setStatusCode("READY");
    planDao.saveAndFlush(plan);
    return plan.getId();
  }

  private Long insertPlanSlice() {
    PlanSliceEntity slice = new PlanSliceEntity();
    slice.setId(SnowflakeIdGenerator.getId());
    slice.setPlanId(testPlanId);
    slice.setProvenanceCode(TEST_PROVENANCE_CODE);
    slice.setSliceNo(0);
    slice.setSliceSignatureHash("slice-sig-hash-" + System.nanoTime());
    slice.setWindowSpec(createSingleWindowSpec());
    slice.setExprHash(TEST_EXPR_HASH);
    slice.setStatusCode("PENDING");
    planSliceDao.saveAndFlush(slice);
    return slice.getId();
  }

  private Long insertTask() {
    TaskEntity task = new TaskEntity();
    task.setId(SnowflakeIdGenerator.getId());
    task.setScheduleInstanceId(testScheduleInstanceId);
    task.setPlanId(testPlanId);
    task.setSliceId(testSliceId);
    task.setProvenanceCode(TEST_PROVENANCE_CODE);
    task.setOperationCode(TEST_OPERATION_CODE);
    task.setIdempotentKey("task-idem-001");
    task.setExprHash(TEST_EXPR_HASH);
    task.setPriority(5);
    task.setRetryCount(0);
    task.setLeaseCount(0);
    task.setStatusCode("PENDING");
    taskDao.saveAndFlush(task);
    return task.getId();
  }

  private Long insertTaskRun() {
    TaskRunEntity run = new TaskRunEntity();
    run.setId(SnowflakeIdGenerator.getId());
    run.setTaskId(testTaskId);
    run.setAttemptNo(1);
    run.setProvenanceCode(TEST_PROVENANCE_CODE);
    run.setOperationCode(TEST_OPERATION_CODE);
    run.setStatusCode("RUNNING");
    run.setStartedAt(Instant.now());
    runDao.saveAndFlush(run);
    return run.getId();
  }

  private ObjectNode createSingleWindowSpec() {
    ObjectNode windowSpec = objectMapper.createObjectNode();
    windowSpec.put("strategy", "SINGLE");
    return windowSpec;
  }

  private TaskRunBatch createTestBatch(int batchNo, String idempotentKey) {
    return new TaskRunBatch(
        null,
        testRunId,
        testTaskId,
        testSliceId,
        testPlanId,
        ProvenanceCode.parse(TEST_PROVENANCE_CODE),
        TEST_OPERATION_CODE,
        batchNo,
        null,
        100,
        null,
        TEST_EXPR_HASH,
        new IdempotentKey(idempotentKey));
  }

  private TaskRunBatch createTestBatchWithId(
      Long id, int batchNo, String idempotentKey, BatchStatus status) {
    return TaskRunBatch.restore(
        id,
        testRunId,
        testTaskId,
        testSliceId,
        testPlanId,
        ProvenanceCode.parse(TEST_PROVENANCE_CODE),
        TEST_OPERATION_CODE,
        batchNo,
        null,
        100,
        null,
        null,
        TEST_EXPR_HASH,
        new IdempotentKey(idempotentKey),
        status,
        null,
        Instant.now(),
        status == BatchStatus.FAILED ? "Test error" : null,
        null);
  }

  private TaskRunBatchEntity createTestBatchEntity(int batchNo, String idempotentKey) {
    TaskRunBatchEntity batch = new TaskRunBatchEntity();
    batch.setId(SnowflakeIdGenerator.getId());
    batch.setRunId(testRunId);
    batch.setTaskId(testTaskId);
    batch.setSliceId(testSliceId);
    batch.setPlanId(testPlanId);
    batch.setProvenanceCode(TEST_PROVENANCE_CODE);
    batch.setOperationCode(TEST_OPERATION_CODE);
    batch.setBatchNo(batchNo);
    batch.setPageSize(100);
    batch.setExprHash(TEST_EXPR_HASH);
    batch.setIdempotentKey(idempotentKey);
    batch.setStatusCode(BatchStatus.RUNNING.getCode());
    batch.setRecordCount(0);
    return batch;
  }
}
