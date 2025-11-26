package com.patra.ingest.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.infra.persistence.converter.TaskConverter;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import com.patra.ingest.infra.persistence.mapper.TaskMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/// TaskRepositoryAdapter 单元测试。
///
/// 测试策略：
///
/// - 使用 Mockito Mock 所有依赖（Mapper, Converter）
///   - 不启动 Spring 容器，纯单元测试
///   - 验证方法调用、参数传递和返回值转换
///   - 覆盖租约操作的 CAS 语义
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskRepositoryAdapter 单元测试")
class TaskRepositoryAdapterTest {

  @Mock private TaskMapper mapper;
  @Mock private TaskConverter converter;

  @InjectMocks private TaskRepositoryAdapter repository;

  private static final Long TEST_TASK_ID = 1L;
  private static final Long TEST_PLAN_ID = 100L;
  private static final Long TEST_SLICE_ID = 200L;
  private static final Long TEST_VERSION = 1L;
  private static final String TEST_IDEMPOTENT_KEY = "task-001";
  private static final String TEST_OWNER = "worker-01";
  private static final String TEST_PROVENANCE_CODE = "PUBMED";
  private static final String TEST_OPERATION_CODE = "SEARCH";

  @Nested
  @DisplayName("保存操作")
  class SaveTests {

    @Test
    @DisplayName("应在 ID 为 null 时插入任务并回写 ID")
    void shouldInsertWhenIdIsNullAndAssignId() {
      // Given
      TaskAggregate aggregate = createTestTaskAggregate(null);
      TaskDO entity = createTestTaskDO(null);
      TaskDO entityWithId = createTestTaskDO(TEST_TASK_ID);

      when(converter.toEntity(aggregate)).thenReturn(entity);
      when(mapper.insert(entity))
          .thenAnswer(
              invocation -> {
                entity.setId(TEST_TASK_ID); // 模拟数据库生成 ID
                return 1;
              });

      // When
      TaskAggregate result = repository.save(aggregate);

      // Then
      assertThat(result).isNotNull();
      verify(converter).toEntity(aggregate);
      verify(mapper).insert(entity);
      verify(aggregate).assignId(TEST_TASK_ID);
      verify(aggregate).assignVersion(anyLong());
    }

    @Test
    @DisplayName("应在 ID 存在时更新任务")
    void shouldUpdateWhenIdExists() {
      // Given
      TaskAggregate aggregate = createTestTaskAggregate(TEST_TASK_ID);
      TaskDO entity = createTestTaskDO(TEST_TASK_ID);
      entity.setVersion(2L);

      when(converter.toEntity(aggregate)).thenReturn(entity);
      when(mapper.updateById(entity)).thenReturn(1);

      // When
      TaskAggregate result = repository.save(aggregate);

      // Then
      assertThat(result).isNotNull();
      verify(converter).toEntity(aggregate);
      verify(mapper).updateById(entity);
      verify(aggregate).assignVersion(2L);
    }
  }

  @Nested
  @DisplayName("批量保存操作")
  class SaveAllTests {

    @Test
    @DisplayName("应批量保存多个任务")
    void shouldSaveAllTasks() {
      // Given
      TaskAggregate task1 = createTestTaskAggregate(null);
      TaskAggregate task2 = createTestTaskAggregate(null);
      List<TaskAggregate> tasks = List.of(task1, task2);

      TaskDO entity1 = createTestTaskDO(null);
      TaskDO entity2 = createTestTaskDO(null);

      when(converter.toEntity(task1)).thenReturn(entity1);
      when(converter.toEntity(task2)).thenReturn(entity2);
      when(mapper.insert(any(TaskDO.class)))
          .thenAnswer(
              invocation -> {
                TaskDO entity = invocation.getArgument(0);
                entity.setId(entity == entity1 ? TEST_TASK_ID : TEST_TASK_ID + 1);
                return 1;
              });

      // When
      List<TaskAggregate> result = repository.saveAll(tasks);

      // Then
      assertThat(result).hasSize(2);
      verify(mapper).insert(entity1);
      verify(mapper).insert(entity2);
      verify(task1).assignId(TEST_TASK_ID);
      verify(task2).assignId(TEST_TASK_ID + 1);
    }

    @Test
    @DisplayName("应在空列表时返回空结果")
    void shouldReturnEmptyWhenListIsEmpty() {
      // When
      List<TaskAggregate> result = repository.saveAll(List.of());

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("查询操作")
  class FindTests {

    @Test
    @DisplayName("应按 planId 查询任务列表")
    void shouldFindTasksByPlanId() {
      // Given
      TaskDO entity1 = createTestTaskDO(1L);
      TaskDO entity2 = createTestTaskDO(2L);
      List<TaskDO> entities = List.of(entity1, entity2);

      TaskAggregate aggregate1 = createTestTaskAggregate(1L);
      TaskAggregate aggregate2 = createTestTaskAggregate(2L);

      when(mapper.selectList(any(QueryWrapper.class))).thenReturn(entities);
      when(converter.toAggregate(entity1)).thenReturn(aggregate1);
      when(converter.toAggregate(entity2)).thenReturn(aggregate2);

      // When
      List<TaskAggregate> result = repository.findByPlanId(TEST_PLAN_ID);

      // Then
      assertThat(result).hasSize(2).containsExactly(aggregate1, aggregate2);
      verify(mapper).selectList(any(QueryWrapper.class));
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
      TaskDO entity = createTestTaskDO(TEST_TASK_ID);
      TaskAggregate aggregate = createTestTaskAggregate(TEST_TASK_ID);

      when(mapper.selectOne(any())).thenReturn(entity);
      when(converter.toAggregate(entity)).thenReturn(aggregate);

      // When
      Optional<TaskAggregate> result = repository.findBySliceId(TEST_SLICE_ID);

      // Then
      assertThat(result).isPresent().contains(aggregate);
      verify(mapper).selectOne(any());
    }

    @Test
    @DisplayName("应在 sliceId 为 null 时抛出异常")
    void shouldThrowExceptionWhenSliceIdIsNull() {
      // When & Then
      assertThatThrownBy(() -> repository.findBySliceId(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sliceId must not be null");
    }

    @Test
    @DisplayName("应按 ID 查询任务")
    void shouldFindTaskById() {
      // Given
      TaskDO entity = createTestTaskDO(TEST_TASK_ID);
      TaskAggregate aggregate = createTestTaskAggregate(TEST_TASK_ID);

      when(mapper.selectById(TEST_TASK_ID)).thenReturn(entity);
      when(converter.toAggregate(entity)).thenReturn(aggregate);

      // When
      Optional<TaskAggregate> result = repository.findById(TEST_TASK_ID);

      // Then
      assertThat(result).isPresent().contains(aggregate);
      verify(mapper).selectById(TEST_TASK_ID);
    }

    @Test
    @DisplayName("应在 ID 不存在时返回空 Optional")
    void shouldReturnEmptyWhenIdNotFound() {
      // Given
      when(mapper.selectById(TEST_TASK_ID)).thenReturn(null);

      // When
      Optional<TaskAggregate> result = repository.findById(TEST_TASK_ID);

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
      when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

      // When
      long count =
          repository.countQueuedTasks(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE), TEST_OPERATION_CODE);

      // Then
      assertThat(count).isEqualTo(5L);
      verify(mapper).selectCount(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("应支持 null 参数统计所有队列任务")
    void shouldCountAllQueuedTasksWhenParametersAreNull() {
      // Given
      when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(10L);

      // When
      long count = repository.countQueuedTasks(null, null);

      // Then
      assertThat(count).isEqualTo(10L);
    }
  }

  @Nested
  @DisplayName("租约操作")
  class LeaseTests {

    @Test
    @DisplayName("应成功获取租约")
    void shouldAcquireLeaseSuccessfully() {
      // Given
      Instant now = Instant.now();
      int ttlSeconds = 300;

      when(mapper.tryAcquireLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds, TEST_IDEMPOTENT_KEY))
          .thenReturn(1);

      // When
      boolean result =
          repository.tryAcquireLease(
              TEST_TASK_ID, TEST_OWNER, now, ttlSeconds, TEST_IDEMPOTENT_KEY);

      // Then
      assertThat(result).isTrue();
      verify(mapper)
          .tryAcquireLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds, TEST_IDEMPOTENT_KEY);
    }

    @Test
    @DisplayName("应在租约被其他持有者持有时获取失败")
    void shouldFailToAcquireLeaseWhenHeldByOthers() {
      // Given
      Instant now = Instant.now();
      int ttlSeconds = 300;

      when(mapper.tryAcquireLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds, TEST_IDEMPOTENT_KEY))
          .thenReturn(0);

      // When
      boolean result =
          repository.tryAcquireLease(
              TEST_TASK_ID, TEST_OWNER, now, ttlSeconds, TEST_IDEMPOTENT_KEY);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应成功标记任务为 RUNNING 并更新租约")
    void shouldMarkRunningWithLeaseSuccessfully() {
      // Given
      Instant now = Instant.now();
      int ttlSeconds = 300;

      when(mapper.markRunningWithLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds)).thenReturn(1);

      // When
      boolean result = repository.markRunningWithLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds);

      // Then
      assertThat(result).isTrue();
      verify(mapper).markRunningWithLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds);
    }

    @Test
    @DisplayName("应在租约丢失时标记 RUNNING 失败")
    void shouldFailToMarkRunningWhenLeaseLost() {
      // Given
      Instant now = Instant.now();
      int ttlSeconds = 300;

      when(mapper.markRunningWithLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds)).thenReturn(0);

      // When
      boolean result = repository.markRunningWithLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应成功续约租约")
    void shouldRenewLeaseSuccessfully() {
      // Given
      Instant now = Instant.now();
      int ttlSeconds = 300;

      when(mapper.renewLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds)).thenReturn(1);

      // When
      boolean result = repository.renewLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds);

      // Then
      assertThat(result).isTrue();
      verify(mapper).renewLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds);
    }

    @Test
    @DisplayName("应在租约丢失时续约失败")
    void shouldFailToRenewLeaseWhenLeaseLost() {
      // Given
      Instant now = Instant.now();
      int ttlSeconds = 300;

      when(mapper.renewLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds)).thenReturn(0);

      // When
      boolean result = repository.renewLease(TEST_TASK_ID, TEST_OWNER, now, ttlSeconds);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应批量续约多个任务的租约")
    void shouldBatchRenewLeasesSuccessfully() {
      // Given
      List<Long> taskIds = List.of(1L, 2L, 3L);
      Instant now = Instant.now();
      int ttlSeconds = 300;

      when(mapper.batchRenewLeases(taskIds, TEST_OWNER, now, ttlSeconds)).thenReturn(3);

      // When
      int result = repository.batchRenewLeases(taskIds, TEST_OWNER, now, ttlSeconds);

      // Then
      assertThat(result).isEqualTo(3);
      verify(mapper).batchRenewLeases(taskIds, TEST_OWNER, now, ttlSeconds);
    }

    @Test
    @DisplayName("应在任务 ID 列表为空时返回 0")
    void shouldReturnZeroWhenTaskIdsIsEmpty() {
      // When
      int result = repository.batchRenewLeases(List.of(), TEST_OWNER, Instant.now(), 300);

      // Then
      assertThat(result).isZero();
    }

    @Test
    @DisplayName("应在任务 ID 列表为 null 时返回 0")
    void shouldReturnZeroWhenTaskIdsIsNull() {
      // When
      int result = repository.batchRenewLeases(null, TEST_OWNER, Instant.now(), 300);

      // Then
      assertThat(result).isZero();
    }
  }

  // ==================== 辅助方法 ====================

  private TaskAggregate createTestTaskAggregate(Long id) {
    TaskAggregate aggregate = org.mockito.Mockito.mock(TaskAggregate.class);
    when(aggregate.getId()).thenReturn(id);
    when(aggregate.getPlanId()).thenReturn(TEST_PLAN_ID);
    when(aggregate.getSliceId()).thenReturn(TEST_SLICE_ID);
    when(aggregate.getIdempotentKey()).thenReturn(TEST_IDEMPOTENT_KEY);
    when(aggregate.getVersion()).thenReturn(TEST_VERSION);
    return aggregate;
  }

  private TaskDO createTestTaskDO(Long id) {
    TaskDO entity = new TaskDO();
    entity.setId(id);
    entity.setPlanId(TEST_PLAN_ID);
    entity.setSliceId(TEST_SLICE_ID);
    entity.setIdempotentKey(TEST_IDEMPOTENT_KEY);
    entity.setVersion(TEST_VERSION);
    entity.setStatusCode("QUEUED");
    entity.setProvenanceCode(TEST_PROVENANCE_CODE);
    entity.setOperationCode(TEST_OPERATION_CODE);
    return entity;
  }
}
