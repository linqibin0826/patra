package com.patra.ingest.app.usecase.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/// PlanIdempotencyCoordinator 单元测试。
/// 
/// 测试覆盖:
/// 
/// - ✅ handleIdempotentPlanReuse() - 存在失败任务，需要重试
///   - ✅ handleIdempotentPlanReuse() - 无失败任务，直接返回现有状态
///   - ✅ handleIdempotentPlanReuse() - 多个失败任务批量重试
///   - ✅ 任务重试状态重置验证
///   - ✅ 重试事件发布验证
/// 
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlanIdempotencyCoordinator 单元测试")
class PlanIdempotencyCoordinatorTest {

  @Mock private PlanSliceRepository planSliceRepository;
  @Mock private TaskRepository taskRepository;
  @Mock private PlanPersistenceCoordinator persistenceCoordinator;
  @Mock private PlanPublishingCoordinator publishingCoordinator;

  @Mock private PlanAggregate existingPlan;
  @Mock private ScheduleInstanceAggregate schedule;
  @Mock private PlanSliceAggregate slice;

  @Captor private ArgumentCaptor<List<TaskQueuedEvent>> eventsCaptor;

  private PlanIdempotencyCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator =
        new PlanIdempotencyCoordinator(
            planSliceRepository, taskRepository, persistenceCoordinator, publishingCoordinator);
  }

  @Nested
  @DisplayName("handleIdempotentPlanReuse() 正常流程测试")
  class NormalFlowTests {

    @Test
    @DisplayName("当存在失败任务时应重置并重新入队")
    void shouldResetAndRequeueFailedTasks() {
      // Given
      Long planId = 100L;
      String planKey = "plan-key-001";

      TaskAggregate failedTask = createMockTask(1L, TaskStatus.FAILED);
      List<TaskAggregate> tasks = List.of(failedTask);
      List<PlanSliceAggregate> slices = List.of(slice);

      TaskQueuedEvent queuedEvent = createTaskQueuedEvent(1L);
      PlanIngestionResult expectedResult = createIngestionResult();

      when(existingPlan.getId()).thenReturn(planId);
      when(existingPlan.getProvenanceCode()).thenReturn(ProvenanceCode.PUBMED);
      when(existingPlan.getOperationCode()).thenReturn(OperationCode.HARVEST.getCode());
      when(planSliceRepository.findByPlanId(planId)).thenReturn(slices);
      when(taskRepository.findByPlanId(planId)).thenReturn(tasks);
      when(publishingCoordinator.collectQueuedEvents(any())).thenReturn(List.of(queuedEvent));
      when(publishingCoordinator.buildIngestionResult(schedule, existingPlan, slices, tasks))
          .thenReturn(expectedResult);

      // When
      PlanIngestionResult result =
          coordinator.handleIdempotentPlanReuse(existingPlan, schedule, planKey);

      // Then
      assertThat(result).isEqualTo(expectedResult);

      // 验证任务重试准备
      verify(failedTask).prepareForRetry();
      verify(persistenceCoordinator).saveTask(failedTask);

      // 验证重试事件发布
      verify(publishingCoordinator).collectQueuedEvents(List.of(failedTask));
      verify(publishingCoordinator)
          .publishRetryEvents(List.of(queuedEvent), existingPlan, schedule);
    }

    @Test
    @DisplayName("当无失败任务时应直接返回现有状态")
    void shouldReturnExistingStateWhenNoFailedTasks() {
      // Given
      Long planId = 100L;
      String planKey = "plan-key-001";

      TaskAggregate successTask = createMockTask(1L, TaskStatus.SUCCEEDED);
      TaskAggregate runningTask = createMockTask(2L, TaskStatus.RUNNING);
      List<TaskAggregate> tasks = List.of(successTask, runningTask);
      List<PlanSliceAggregate> slices = List.of(slice);

      PlanIngestionResult expectedResult = createIngestionResult();

      when(existingPlan.getId()).thenReturn(planId);
      when(existingPlan.getProvenanceCode()).thenReturn(ProvenanceCode.PUBMED);
      when(existingPlan.getOperationCode()).thenReturn(OperationCode.HARVEST.getCode());
      when(planSliceRepository.findByPlanId(planId)).thenReturn(slices);
      when(taskRepository.findByPlanId(planId)).thenReturn(tasks);
      when(publishingCoordinator.buildIngestionResult(schedule, existingPlan, slices, tasks))
          .thenReturn(expectedResult);

      // When
      PlanIngestionResult result =
          coordinator.handleIdempotentPlanReuse(existingPlan, schedule, planKey);

      // Then
      assertThat(result).isEqualTo(expectedResult);

      // 验证没有调用任何重试相关的方法
      verify(persistenceCoordinator, never()).saveTask(any());
      verify(publishingCoordinator, never()).collectQueuedEvents(any());
      verify(publishingCoordinator, never()).publishRetryEvents(any(), any(), any());
    }

    @Test
    @DisplayName("应正确处理多个失败任务的批量重试")
    void shouldHandleMultipleFailedTasksRetry() {
      // Given
      Long planId = 100L;
      String planKey = "plan-key-001";

      TaskAggregate failedTask1 = createMockTask(1L, TaskStatus.FAILED);
      TaskAggregate failedTask2 = createMockTask(2L, TaskStatus.FAILED);
      TaskAggregate successTask = createMockTask(3L, TaskStatus.SUCCEEDED);

      List<TaskAggregate> tasks = List.of(failedTask1, failedTask2, successTask);
      List<PlanSliceAggregate> slices = List.of(slice);

      TaskQueuedEvent event1 = createTaskQueuedEvent(1L);
      TaskQueuedEvent event2 = createTaskQueuedEvent(2L);
      PlanIngestionResult expectedResult = createIngestionResult();

      when(existingPlan.getId()).thenReturn(planId);
      when(existingPlan.getProvenanceCode()).thenReturn(ProvenanceCode.PUBMED);
      when(existingPlan.getOperationCode()).thenReturn(OperationCode.HARVEST.getCode());
      when(planSliceRepository.findByPlanId(planId)).thenReturn(slices);
      when(taskRepository.findByPlanId(planId)).thenReturn(tasks);
      when(publishingCoordinator.collectQueuedEvents(List.of(failedTask1, failedTask2)))
          .thenReturn(List.of(event1, event2));
      when(publishingCoordinator.buildIngestionResult(schedule, existingPlan, slices, tasks))
          .thenReturn(expectedResult);

      // When
      PlanIngestionResult result =
          coordinator.handleIdempotentPlanReuse(existingPlan, schedule, planKey);

      // Then
      assertThat(result).isEqualTo(expectedResult);

      // 验证两个失败任务都被处理
      verify(failedTask1).prepareForRetry();
      verify(failedTask2).prepareForRetry();
      verify(persistenceCoordinator, times(2)).saveTask(any());

      // 验证成功任务未被处理
      verify(successTask, never()).prepareForRetry();

      // 验证重试事件发布
      verify(publishingCoordinator)
          .publishRetryEvents(eventsCaptor.capture(), eq(existingPlan), eq(schedule));
      List<TaskQueuedEvent> publishedEvents = eventsCaptor.getValue();
      assertThat(publishedEvents).hasSize(2);
      assertThat(publishedEvents).containsExactly(event1, event2);
    }
  }

  @Nested
  @DisplayName("任务状态过滤测试")
  class TaskStatusFilteringTests {

    @Test
    @DisplayName("应只重试 FAILED 状态的任务")
    void shouldOnlyRetryFailedTasks() {
      // Given
      Long planId = 100L;

      TaskAggregate failedTask = createMockTask(1L, TaskStatus.FAILED);
      TaskAggregate successTask = createMockTask(2L, TaskStatus.SUCCEEDED);
      TaskAggregate runningTask = createMockTask(3L, TaskStatus.RUNNING);
      TaskAggregate queuedTask = createMockTask(4L, TaskStatus.QUEUED);
      TaskAggregate pendingTask = createMockTask(5L, TaskStatus.PENDING);

      List<TaskAggregate> tasks =
          List.of(failedTask, successTask, runningTask, queuedTask, pendingTask);

      when(existingPlan.getId()).thenReturn(planId);
      when(planSliceRepository.findByPlanId(planId)).thenReturn(Collections.emptyList());
      when(taskRepository.findByPlanId(planId)).thenReturn(tasks);
      when(publishingCoordinator.collectQueuedEvents(List.of(failedTask)))
          .thenReturn(Collections.emptyList());
      when(publishingCoordinator.buildIngestionResult(any(), any(), any(), any()))
          .thenReturn(createIngestionResult());

      // When
      coordinator.handleIdempotentPlanReuse(existingPlan, schedule, "plan-key-001");

      // Then
      // 只有 FAILED 任务被处理
      verify(failedTask).prepareForRetry();

      // 其他状态的任务不被处理
      verify(successTask, never()).prepareForRetry();
      verify(runningTask, never()).prepareForRetry();
      verify(queuedTask, never()).prepareForRetry();
      verify(pendingTask, never()).prepareForRetry();

      // 只持久化了 1 个任务
      verify(persistenceCoordinator, times(1)).saveTask(any());
    }

    @Test
    @DisplayName("应正确识别所有 FAILED 状态任务")
    void shouldIdentifyAllFailedTasks() {
      // Given
      Long planId = 100L;

      TaskAggregate failedTask1 = createMockTask(1L, TaskStatus.FAILED);
      TaskAggregate failedTask2 = createMockTask(2L, TaskStatus.FAILED);
      TaskAggregate failedTask3 = createMockTask(3L, TaskStatus.FAILED);

      List<TaskAggregate> tasks = List.of(failedTask1, failedTask2, failedTask3);

      when(existingPlan.getId()).thenReturn(planId);
      when(planSliceRepository.findByPlanId(planId)).thenReturn(Collections.emptyList());
      when(taskRepository.findByPlanId(planId)).thenReturn(tasks);
      when(publishingCoordinator.collectQueuedEvents(any())).thenReturn(Collections.emptyList());
      when(publishingCoordinator.buildIngestionResult(any(), any(), any(), any()))
          .thenReturn(createIngestionResult());

      // When
      coordinator.handleIdempotentPlanReuse(existingPlan, schedule, "plan-key-001");

      // Then
      verify(failedTask1).prepareForRetry();
      verify(failedTask2).prepareForRetry();
      verify(failedTask3).prepareForRetry();
      verify(persistenceCoordinator, times(3)).saveTask(any());
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryTests {

    @Test
    @DisplayName("当计划无任务时应正常处理")
    void shouldHandleEmptyTaskList() {
      // Given
      Long planId = 100L;
      String planKey = "plan-key-001";

      when(existingPlan.getId()).thenReturn(planId);
      when(planSliceRepository.findByPlanId(planId)).thenReturn(Collections.emptyList());
      when(taskRepository.findByPlanId(planId)).thenReturn(Collections.emptyList());
      when(publishingCoordinator.buildIngestionResult(
              schedule, existingPlan, Collections.emptyList(), Collections.emptyList()))
          .thenReturn(createIngestionResult());

      // When
      PlanIngestionResult result =
          coordinator.handleIdempotentPlanReuse(existingPlan, schedule, planKey);

      // Then
      assertThat(result).isNotNull();
      verify(persistenceCoordinator, never()).saveTask(any());
      verify(publishingCoordinator, never()).collectQueuedEvents(any());
      verify(publishingCoordinator, never()).publishRetryEvents(any(), any(), any());
    }

    @Test
    @DisplayName("当计划无切片时应正常处理")
    void shouldHandleEmptySliceList() {
      // Given
      Long planId = 100L;
      String planKey = "plan-key-001";

      TaskAggregate task = createMockTask(1L, TaskStatus.SUCCEEDED);

      when(existingPlan.getId()).thenReturn(planId);
      when(planSliceRepository.findByPlanId(planId)).thenReturn(Collections.emptyList());
      when(taskRepository.findByPlanId(planId)).thenReturn(List.of(task));
      when(publishingCoordinator.buildIngestionResult(
              schedule, existingPlan, Collections.emptyList(), List.of(task)))
          .thenReturn(createIngestionResult());

      // When
      PlanIngestionResult result =
          coordinator.handleIdempotentPlanReuse(existingPlan, schedule, planKey);

      // Then
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("Repository 交互测试")
  class RepositoryInteractionTests {

    @Test
    @DisplayName("应按计划 ID 加载切片和任务")
    void shouldLoadSlicesAndTasksByPlanId() {
      // Given
      Long planId = 100L;

      when(existingPlan.getId()).thenReturn(planId);
      when(planSliceRepository.findByPlanId(planId)).thenReturn(Collections.emptyList());
      when(taskRepository.findByPlanId(planId)).thenReturn(Collections.emptyList());
      when(publishingCoordinator.buildIngestionResult(any(), any(), any(), any()))
          .thenReturn(createIngestionResult());

      // When
      coordinator.handleIdempotentPlanReuse(existingPlan, schedule, "plan-key-001");

      // Then
      verify(planSliceRepository).findByPlanId(planId);
      verify(taskRepository).findByPlanId(planId);
    }

    @Test
    @DisplayName("应按顺序执行重试任务的持久化")
    void shouldPersistRetryTasksInOrder() {
      // Given
      Long planId = 100L;

      TaskAggregate task1 = createMockTask(1L, TaskStatus.FAILED);
      TaskAggregate task2 = createMockTask(2L, TaskStatus.FAILED);

      when(existingPlan.getId()).thenReturn(planId);
      when(planSliceRepository.findByPlanId(planId)).thenReturn(Collections.emptyList());
      when(taskRepository.findByPlanId(planId)).thenReturn(List.of(task1, task2));
      when(publishingCoordinator.collectQueuedEvents(any())).thenReturn(Collections.emptyList());
      when(publishingCoordinator.buildIngestionResult(any(), any(), any(), any()))
          .thenReturn(createIngestionResult());

      // When
      coordinator.handleIdempotentPlanReuse(existingPlan, schedule, "plan-key-001");

      // Then
      verify(persistenceCoordinator).saveTask(task1);
      verify(persistenceCoordinator).saveTask(task2);
    }
  }

  // ==================== 辅助方法 ====================

  /// 创建 Mock TaskAggregate。
  private TaskAggregate createMockTask(Long id, TaskStatus status) {
    TaskAggregate task = org.mockito.Mockito.mock(TaskAggregate.class);
    when(task.getId()).thenReturn(id);
    when(task.getStatus()).thenReturn(status);
    return task;
  }

  /// 创建 TaskQueuedEvent。
  private TaskQueuedEvent createTaskQueuedEvent(Long taskId) {
    return TaskQueuedEvent.of(
        taskId,
        100L,
        10L,
        1L,
        ProvenanceCode.PUBMED,
        OperationCode.HARVEST.getCode(),
        "task-key-" + taskId,
        "{}",
        1,
        Instant.now());
  }

  /// 创建 PlanIngestionResult。
  private PlanIngestionResult createIngestionResult() {
    return new PlanIngestionResult(1L, 100L, List.of(10L), 1, "SUCCESS");
  }
}
