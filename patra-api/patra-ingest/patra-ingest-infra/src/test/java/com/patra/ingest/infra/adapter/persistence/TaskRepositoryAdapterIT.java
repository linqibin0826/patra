package com.patra.ingest.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import com.patra.ingest.infra.persistence.mapper.PlanSliceMapper;
import com.patra.ingest.infra.persistence.mapper.ScheduleInstanceMapper;
import com.patra.ingest.infra.persistence.mapper.TaskMapper;
import com.patra.ingest.infra.config.IngestMySQLContainerInitializer;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/// TaskRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + MySQL 8 测试任务聚合根持久化。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 MySQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：save、findByPlanId、findBySliceId、findById、租约操作
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = IngestMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  TaskRepositoryAdapter.class,
  TestMybatisPlusAutoConfiguration.class,
  JacksonAutoConfiguration.class
})
@ComponentScan("com.patra.ingest.infra.persistence.converter")
@MapperScan("com.patra.ingest.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("TaskRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class TaskRepositoryAdapterIT {

  @Autowired private TaskRepositoryAdapter repository;

  @Autowired private TaskMapper taskMapper;
  @Autowired private PlanMapper planMapper;
  @Autowired private PlanSliceMapper planSliceMapper;
  @Autowired private ScheduleInstanceMapper scheduleInstanceMapper;
  @Autowired private ObjectMapper objectMapper;

  private static final String TEST_PROVENANCE_CODE = "PUBMED";
  private static final String TEST_OPERATION_CODE = "HARVEST";
  private static final String TEST_OWNER = "worker-01";

  private Long testScheduleInstanceId;
  private Long testPlanId;
  private Long testSliceId;

  @BeforeEach
  void setUp() {
    // 清理现有数据（按外键依赖顺序）
    taskMapper.delete(Wrappers.<TaskDO>lambdaQuery().ne(TaskDO::getId, 0L));
    planSliceMapper.delete(Wrappers.<PlanSliceDO>lambdaQuery().ne(PlanSliceDO::getId, 0L));
    planMapper.delete(Wrappers.<PlanDO>lambdaQuery().ne(PlanDO::getId, 0L));
    scheduleInstanceMapper.delete(
        Wrappers.<ScheduleInstanceDO>lambdaQuery().ne(ScheduleInstanceDO::getId, 0L));

    // 创建依赖数据
    testScheduleInstanceId = insertScheduleInstance();
    testPlanId = insertPlan();
    testSliceId = insertPlanSlice();
  }

  @Nested
  @DisplayName("保存操作")
  class SaveTests {

    @Test
    @DisplayName("应在通过 Mapper 插入后通过 findById 查询到任务")
    void shouldFindTaskAfterMapperInsert() {
      // Given: 通过 Mapper 直接插入测试数据
      TaskDO taskDO = createTestTaskDO("task-001");
      taskMapper.insert(taskDO);

      // When
      Optional<TaskAggregate> result = repository.findById(taskDO.getId());

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getIdempotentKey()).isEqualTo("task-001");
      assertThat(result.get().getProvenanceCode().getCode()).isEqualTo(TEST_PROVENANCE_CODE);
    }
  }

  @Nested
  @DisplayName("查询操作")
  class FindTests {

    @Test
    @DisplayName("应按 planId 查询任务列表")
    void shouldFindTasksByPlanId() {
      // Given
      TaskDO taskDO = createTestTaskDO("task-001");
      taskMapper.insert(taskDO);

      // When
      List<TaskAggregate> result = repository.findByPlanId(testPlanId);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getPlanId()).isEqualTo(testPlanId);
    }

    @Test
    @DisplayName("应在 planId 为 null 时返回空列表")
    void shouldReturnEmptyListWhenPlanIdIsNull() {
      // When
      List<TaskAggregate> result = repository.findByPlanId(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应按 sliceId 查询任务")
    void shouldFindTaskBySliceId() {
      // Given
      TaskDO taskDO = createTestTaskDO("task-001");
      taskMapper.insert(taskDO);

      // When
      Optional<TaskAggregate> result = repository.findBySliceId(testSliceId);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getSliceId()).isEqualTo(testSliceId);
    }

    @Test
    @DisplayName("应按 ID 查询任务")
    void shouldFindTaskById() {
      // Given
      TaskDO taskDO = createTestTaskDO("task-001");
      taskMapper.insert(taskDO);

      // When
      Optional<TaskAggregate> result = repository.findById(taskDO.getId());

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getIdempotentKey()).isEqualTo("task-001");
    }

    @Test
    @DisplayName("应在 ID 不存在时返回空 Optional")
    void shouldReturnEmptyWhenIdNotFound() {
      // Given: 不插入任何任务

      // When
      Optional<TaskAggregate> result = repository.findById(999L);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("统计操作")
  class CountTests {

    @Test
    @DisplayName("应统计队列中的任务数量")
    void shouldCountQueuedTasks() {
      // Given
      TaskDO taskDO = createTestTaskDO("task-001");
      taskDO.setStatusCode("QUEUED");
      taskMapper.insert(taskDO);

      // When
      long count =
          repository.countQueuedTasks(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE), TEST_OPERATION_CODE);

      // Then
      assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("应支持 null 参数统计所有队列任务")
    void shouldCountAllQueuedTasksWhenParametersAreNull() {
      // Given
      TaskDO taskDO = createTestTaskDO("task-001");
      taskDO.setStatusCode("QUEUED");
      taskMapper.insert(taskDO);

      // When
      long count = repository.countQueuedTasks(null, null);

      // Then
      assertThat(count).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("租约操作")
  class LeaseTests {

    @Test
    @DisplayName("应成功获取租约")
    void shouldAcquireLeaseSuccessfully() {
      // Given
      TaskDO taskDO = createTestTaskDO("task-001");
      taskDO.setStatusCode("QUEUED");
      taskMapper.insert(taskDO);

      Instant now = Instant.now();
      int ttlSeconds = 300;

      // When
      boolean result =
          repository.tryAcquireLease(taskDO.getId(), TEST_OWNER, now, ttlSeconds, "task-001");

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应成功续约租约")
    void shouldRenewLeaseSuccessfully() {
      // Given: 先获取租约
      TaskDO taskDO = createTestTaskDO("task-001");
      taskDO.setStatusCode("QUEUED");
      taskMapper.insert(taskDO);

      Instant now = Instant.now();
      int ttlSeconds = 300;
      repository.tryAcquireLease(taskDO.getId(), TEST_OWNER, now, ttlSeconds, "task-001");

      // When
      boolean result = repository.renewLease(taskDO.getId(), TEST_OWNER, now, ttlSeconds);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应批量续约多个任务的租约")
    void shouldBatchRenewLeasesSuccessfully() {
      // Given
      Long sliceId2 = insertPlanSlice();

      TaskDO task1 = createTestTaskDO("task-001");
      task1.setStatusCode("QUEUED");
      taskMapper.insert(task1);

      TaskDO task2 = createTestTaskDOWithSlice("task-002", sliceId2);
      task2.setStatusCode("QUEUED");
      taskMapper.insert(task2);

      Instant now = Instant.now();
      int ttlSeconds = 300;
      repository.tryAcquireLease(task1.getId(), TEST_OWNER, now, ttlSeconds, "task-001");
      repository.tryAcquireLease(task2.getId(), TEST_OWNER, now, ttlSeconds, "task-002");

      List<Long> taskIds = List.of(task1.getId(), task2.getId());

      // When
      int result = repository.batchRenewLeases(taskIds, TEST_OWNER, now, ttlSeconds);

      // Then
      assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("应在任务 ID 列表为空时返回 0")
    void shouldReturnZeroWhenTaskIdsIsEmpty() {
      // When
      int result = repository.batchRenewLeases(List.of(), TEST_OWNER, Instant.now(), 300);

      // Then
      assertThat(result).isZero();
    }
  }

  // ==================== 辅助方法 ====================

  private Long insertScheduleInstance() {
    ScheduleInstanceDO instance = new ScheduleInstanceDO();
    instance.setSchedulerCode("XXL");
    instance.setTriggerTypeCode("SCHEDULE");
    instance.setTriggeredAt(Instant.now());
    instance.setProvenanceCode(TEST_PROVENANCE_CODE);
    scheduleInstanceMapper.insert(instance);
    return instance.getId();
  }

  private Long insertPlan() {
    PlanDO plan = new PlanDO();
    plan.setScheduleInstanceId(testScheduleInstanceId);
    plan.setPlanKey("PUBMED-HARVEST-2025-01-01");
    plan.setProvenanceCode(TEST_PROVENANCE_CODE);
    plan.setOperationCode(TEST_OPERATION_CODE);
    plan.setSliceStrategyCode("SINGLE");
    plan.setExprProtoHash("test-expr-hash-001");
    plan.setWindowSpec(createSingleWindowSpec());
    plan.setStatusCode("READY");
    planMapper.insert(plan);
    return plan.getId();
  }

  private Long insertPlanSlice() {
    PlanSliceDO slice = new PlanSliceDO();
    slice.setPlanId(testPlanId);
    slice.setProvenanceCode(TEST_PROVENANCE_CODE);
    slice.setSliceNo(planSliceMapper.selectCount(null).intValue());
    slice.setSliceSignatureHash("slice-sig-hash-" + System.nanoTime());
    slice.setWindowSpec(createSingleWindowSpec());
    slice.setExprHash("expr-hash-001");
    slice.setStatusCode("PENDING");
    planSliceMapper.insert(slice);
    return slice.getId();
  }

  private ObjectNode createSingleWindowSpec() {
    ObjectNode windowSpec = objectMapper.createObjectNode();
    windowSpec.put("strategy", "SINGLE");
    return windowSpec;
  }

  private TaskDO createTestTaskDO(String idempotentKey) {
    return createTestTaskDOWithSlice(idempotentKey, testSliceId);
  }

  private TaskDO createTestTaskDOWithSlice(String idempotentKey, Long sliceId) {
    TaskDO task = new TaskDO();
    task.setScheduleInstanceId(testScheduleInstanceId);
    task.setPlanId(testPlanId);
    task.setSliceId(sliceId);
    task.setProvenanceCode(TEST_PROVENANCE_CODE);
    task.setOperationCode(TEST_OPERATION_CODE);
    task.setIdempotentKey(idempotentKey);
    task.setExprHash("expr-hash-001");
    task.setPriority(5);
    task.setRetryCount(0);
    task.setLeaseCount(0);
    task.setStatusCode("PENDING");
    return task;
  }
}
