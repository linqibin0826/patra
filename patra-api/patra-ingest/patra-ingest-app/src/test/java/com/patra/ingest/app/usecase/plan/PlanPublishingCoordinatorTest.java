package com.patra.ingest.app.usecase.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.app.usecase.plan.publisher.TaskOutboxPublisher;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import com.patra.ingest.domain.model.vo.slice.PlanSliceId;
import com.patra.ingest.domain.model.vo.task.TaskId;
import java.time.Instant;
import java.util.ArrayList;
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

/// PlanPublishingCoordinator 单元测试。
///
/// 测试覆盖:
///
/// - ✅ publishNewPlanEvents() - 正常发布新计划事件
///   - ✅ publishRetryEvents() - 正常发布重试事件
///   - ✅ collectQueuedEvents() - 正常收集任务事件
///   - ✅ collectQueuedEvents() - 处理空任务列表
///   - ✅ buildIngestionResult() - 构建结果（带任务列表）
///   - ✅ buildIngestionResult() - 构建结果（带任务数量）
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlanPublishingCoordinator 单元测试")
class PlanPublishingCoordinatorTest {

  @Mock private TaskOutboxPublisher taskOutboxPublisher;

  @Mock private PlanAggregate plan;
  @Mock private ScheduleInstanceAggregate schedule;
  @Mock private PlanSliceAggregate slice1;
  @Mock private PlanSliceAggregate slice2;
  @Mock private TaskAggregate task1;
  @Mock private TaskAggregate task2;
  @Mock private TaskAggregate task3;

  @Captor private ArgumentCaptor<List<TaskQueuedEvent>> eventsCaptor;

  private PlanPublishingCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new PlanPublishingCoordinator(taskOutboxPublisher);
  }

  @Nested
  @DisplayName("publishNewPlanEvents() 测试")
  class PublishNewPlanEventsTests {

    @Test
    @DisplayName("应成功发布新计划的任务就绪事件")
    void shouldPublishNewPlanEventsSuccessfully() {
      // Given
      TaskQueuedEvent event1 = createTaskQueuedEvent(1L);
      TaskQueuedEvent event2 = createTaskQueuedEvent(2L);
      List<TaskQueuedEvent> events = List.of(event1, event2);

      when(plan.getId()).thenReturn(PlanId.of(100L));

      // When
      coordinator.publishNewPlanEvents(events, plan, schedule);

      // Then
      verify(taskOutboxPublisher).publish(events, plan, schedule);
    }

    @Test
    @DisplayName("应能处理空事件列表")
    void shouldHandleEmptyEventList() {
      // Given
      List<TaskQueuedEvent> emptyEvents = Collections.emptyList();

      // When
      coordinator.publishNewPlanEvents(emptyEvents, plan, schedule);

      // Then
      verify(taskOutboxPublisher).publish(emptyEvents, plan, schedule);
    }

    @Test
    @DisplayName("应能处理多个事件的批量发布")
    void shouldHandleMultipleEventsPublishing() {
      // Given
      List<TaskQueuedEvent> events = new ArrayList<>();
      for (int i = 1; i <= 10; i++) {
        events.add(createTaskQueuedEvent((long) i));
      }

      when(plan.getId()).thenReturn(PlanId.of(100L));

      // When
      coordinator.publishNewPlanEvents(events, plan, schedule);

      // Then
      verify(taskOutboxPublisher).publish(eventsCaptor.capture(), eq(plan), eq(schedule));
      assertThat(eventsCaptor.getValue()).hasSize(10);
    }
  }

  @Nested
  @DisplayName("publishRetryEvents() 测试")
  class PublishRetryEventsTests {

    @Test
    @DisplayName("应成功发布重试事件")
    void shouldPublishRetryEventsSuccessfully() {
      // Given
      TaskQueuedEvent event1 = createTaskQueuedEvent(1L);
      TaskQueuedEvent event2 = createTaskQueuedEvent(2L);
      List<TaskQueuedEvent> retryEvents = List.of(event1, event2);

      when(plan.getId()).thenReturn(PlanId.of(100L));

      // When
      coordinator.publishRetryEvents(retryEvents, plan, schedule);

      // Then
      verify(taskOutboxPublisher).publishRetry(retryEvents, plan, schedule);
    }

    @Test
    @DisplayName("应能处理空重试事件列表")
    void shouldHandleEmptyRetryEventList() {
      // Given
      List<TaskQueuedEvent> emptyRetryEvents = Collections.emptyList();

      // When
      coordinator.publishRetryEvents(emptyRetryEvents, plan, schedule);

      // Then
      verify(taskOutboxPublisher).publishRetry(emptyRetryEvents, plan, schedule);
    }

    @Test
    @DisplayName("应能处理多个重试事件")
    void shouldHandleMultipleRetryEvents() {
      // Given
      List<TaskQueuedEvent> retryEvents = new ArrayList<>();
      for (int i = 1; i <= 5; i++) {
        retryEvents.add(createTaskQueuedEvent((long) i));
      }

      when(plan.getId()).thenReturn(PlanId.of(100L));

      // When
      coordinator.publishRetryEvents(retryEvents, plan, schedule);

      // Then
      verify(taskOutboxPublisher).publishRetry(eventsCaptor.capture(), eq(plan), eq(schedule));
      assertThat(eventsCaptor.getValue()).hasSize(5);
    }
  }

  @Nested
  @DisplayName("collectQueuedEvents() 测试")
  class CollectQueuedEventsTests {

    @Test
    @DisplayName("应成功从任务聚合根收集事件")
    void shouldCollectEventsFromTaskAggregates() {
      // Given
      TaskQueuedEvent event1 = createTaskQueuedEvent(1L);
      TaskQueuedEvent event2 = createTaskQueuedEvent(2L);

      when(task1.pullDomainEvents()).thenReturn(List.of(event1));
      when(task2.pullDomainEvents()).thenReturn(List.of(event2));

      List<TaskAggregate> tasks = List.of(task1, task2);

      // When
      List<TaskQueuedEvent> events = coordinator.collectQueuedEvents(tasks);

      // Then
      assertThat(events).hasSize(2);
      assertThat(events).containsExactly(event1, event2);

      // 验证显式触发事件
      verify(task1).raiseQueuedEvent();
      verify(task2).raiseQueuedEvent();
    }

    @Test
    @DisplayName("应处理空任务列表并返回空列表")
    void shouldReturnEmptyListWhenTasksAreEmpty() {
      // Given
      List<TaskAggregate> emptyTasks = Collections.emptyList();

      // When
      List<TaskQueuedEvent> events = coordinator.collectQueuedEvents(emptyTasks);

      // Then
      assertThat(events).isEmpty();
      verify(task1, never()).raiseQueuedEvent();
    }

    @Test
    @DisplayName("应处理 null 任务列表并返回空列表")
    void shouldReturnEmptyListWhenTasksAreNull() {
      // Given
      List<TaskAggregate> nullTasks = null;

      // When
      List<TaskQueuedEvent> events = coordinator.collectQueuedEvents(nullTasks);

      // Then
      assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("应正确过滤并收集 TaskQueuedEvent 类型的事件")
    void shouldFilterAndCollectOnlyTaskQueuedEvents() {
      // Given
      TaskQueuedEvent queuedEvent = createTaskQueuedEvent(1L);
      // 创建其他类型的 DomainEvent
      com.patra.common.domain.DomainEvent otherEvent =
          new com.patra.common.domain.DomainEvent() {
            @Override
            public Instant occurredAt() {
              return Instant.now();
            }
          };

      when(task1.pullDomainEvents()).thenReturn(List.of(queuedEvent, otherEvent));

      // When
      List<TaskQueuedEvent> events = coordinator.collectQueuedEvents(List.of(task1));

      // Then
      assertThat(events).hasSize(1);
      assertThat(events).containsExactly(queuedEvent);
    }

    @Test
    @DisplayName("应处理任务没有事件的情况")
    void shouldHandleTasksWithNoEvents() {
      // Given
      when(task1.pullDomainEvents()).thenReturn(Collections.emptyList());
      when(task2.pullDomainEvents()).thenReturn(Collections.emptyList());

      // When
      List<TaskQueuedEvent> events = coordinator.collectQueuedEvents(List.of(task1, task2));

      // Then
      assertThat(events).isEmpty();
      verify(task1).raiseQueuedEvent();
      verify(task2).raiseQueuedEvent();
    }

    @Test
    @DisplayName("应处理多个任务的批量事件收集")
    void shouldCollectEventsFromMultipleTasks() {
      // Given
      TaskQueuedEvent event1 = createTaskQueuedEvent(1L);
      TaskQueuedEvent event2 = createTaskQueuedEvent(2L);
      TaskQueuedEvent event3 = createTaskQueuedEvent(3L);

      when(task1.pullDomainEvents()).thenReturn(List.of(event1));
      when(task2.pullDomainEvents()).thenReturn(List.of(event2));
      when(task3.pullDomainEvents()).thenReturn(List.of(event3));

      List<TaskAggregate> tasks = List.of(task1, task2, task3);

      // When
      List<TaskQueuedEvent> events = coordinator.collectQueuedEvents(tasks);

      // Then
      assertThat(events).hasSize(3);
      assertThat(events).containsExactly(event1, event2, event3);
      verify(task1).raiseQueuedEvent();
      verify(task2).raiseQueuedEvent();
      verify(task3).raiseQueuedEvent();
    }
  }

  @Nested
  @DisplayName("buildIngestionResult() 测试")
  class BuildIngestionResultTests {

    @Test
    @DisplayName("应正确构建接入结果（带任务列表）")
    void shouldBuildIngestionResultWithTaskList() {
      // Given
      Long scheduleId = 1L;
      Long planId = 100L;
      Long sliceId1 = 10L;
      Long sliceId2 = 20L;
      Long taskId1 = 1L;
      Long taskId2 = 2L;

      when(schedule.getId()).thenReturn(ScheduleInstanceId.of(scheduleId));
      when(plan.getId()).thenReturn(PlanId.of(planId));
      when(plan.getStatus()).thenReturn(PlanStatus.READY);
      when(slice1.getId()).thenReturn(PlanSliceId.of(sliceId1));
      when(slice2.getId()).thenReturn(PlanSliceId.of(sliceId2));
      when(task1.getId()).thenReturn(TaskId.of(taskId1));
      when(task2.getId()).thenReturn(TaskId.of(taskId2));

      List<PlanSliceAggregate> slices = List.of(slice1, slice2);
      List<TaskAggregate> tasks = List.of(task1, task2);

      // When
      PlanIngestionResult result = coordinator.buildIngestionResult(schedule, plan, slices, tasks);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.scheduleInstanceId()).isEqualTo(scheduleId);
      assertThat(result.planId()).isEqualTo(planId);
      assertThat(result.sliceIds()).containsExactly(sliceId1, sliceId2);
      assertThat(result.taskCount()).isEqualTo(2);
      assertThat(result.finalStatus()).isEqualTo("READY");
    }

    @Test
    @DisplayName("应正确构建接入结果（带任务数量）")
    void shouldBuildIngestionResultWithTaskCount() {
      // Given
      Long scheduleId = 1L;
      Long planId = 100L;
      Long sliceId1 = 10L;
      Long sliceId2 = 20L;
      int taskCount = 5;
      String statusName = "SUCCESS";

      when(schedule.getId()).thenReturn(ScheduleInstanceId.of(scheduleId));
      when(plan.getId()).thenReturn(PlanId.of(planId));
      when(slice1.getId()).thenReturn(PlanSliceId.of(sliceId1));
      when(slice2.getId()).thenReturn(PlanSliceId.of(sliceId2));

      List<PlanSliceAggregate> slices = List.of(slice1, slice2);

      // When
      PlanIngestionResult result =
          coordinator.buildIngestionResult(schedule, plan, slices, taskCount, statusName);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.scheduleInstanceId()).isEqualTo(scheduleId);
      assertThat(result.planId()).isEqualTo(planId);
      assertThat(result.sliceIds()).containsExactly(sliceId1, sliceId2);
      assertThat(result.taskCount()).isEqualTo(taskCount);
      assertThat(result.finalStatus()).isEqualTo(statusName);
    }

    @Test
    @DisplayName("应处理空切片列表")
    void shouldHandleEmptySliceList() {
      // Given
      when(schedule.getId()).thenReturn(ScheduleInstanceId.of(1L));
      when(plan.getId()).thenReturn(PlanId.of(100L));
      when(plan.getStatus()).thenReturn(PlanStatus.READY);

      List<PlanSliceAggregate> emptySlices = Collections.emptyList();
      List<TaskAggregate> emptyTasks = Collections.emptyList();

      // When
      PlanIngestionResult result =
          coordinator.buildIngestionResult(schedule, plan, emptySlices, emptyTasks);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.sliceIds()).isEmpty();
      assertThat(result.taskCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("应处理空任务列表")
    void shouldHandleEmptyTaskList() {
      // Given
      when(schedule.getId()).thenReturn(ScheduleInstanceId.of(1L));
      when(plan.getId()).thenReturn(PlanId.of(100L));
      when(plan.getStatus()).thenReturn(PlanStatus.READY);
      when(slice1.getId()).thenReturn(PlanSliceId.of(10L));

      List<PlanSliceAggregate> slices = List.of(slice1);
      List<TaskAggregate> emptyTasks = Collections.emptyList();

      // When
      PlanIngestionResult result =
          coordinator.buildIngestionResult(schedule, plan, slices, emptyTasks);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.sliceIds()).hasSize(1);
      assertThat(result.taskCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("应正确映射计划状态到结果状态")
    void shouldMapPlanStatusToFinalStatus() {
      // Given
      when(schedule.getId()).thenReturn(ScheduleInstanceId.of(1L));
      when(plan.getId()).thenReturn(PlanId.of(100L));
      when(plan.getStatus()).thenReturn(PlanStatus.ARCHIVED);

      List<PlanSliceAggregate> slices = Collections.emptyList();
      List<TaskAggregate> tasks = Collections.emptyList();

      // When
      PlanIngestionResult result = coordinator.buildIngestionResult(schedule, plan, slices, tasks);

      // Then
      assertThat(result.finalStatus()).isEqualTo("ARCHIVED");
    }
  }

  @Nested
  @DisplayName("集成场景测试")
  class IntegrationScenarioTests {

    @Test
    @DisplayName("完整流程：收集事件 → 发布新计划 → 构建结果")
    void shouldExecuteCompletePublishingFlow() {
      // Given
      TaskQueuedEvent event1 = createTaskQueuedEvent(1L);
      TaskQueuedEvent event2 = createTaskQueuedEvent(2L);

      when(task1.pullDomainEvents()).thenReturn(List.of(event1));
      when(task2.pullDomainEvents()).thenReturn(List.of(event2));
      when(plan.getId()).thenReturn(PlanId.of(100L));
      when(schedule.getId()).thenReturn(ScheduleInstanceId.of(1L));
      when(plan.getStatus()).thenReturn(PlanStatus.READY);
      when(slice1.getId()).thenReturn(PlanSliceId.of(10L));

      List<TaskAggregate> tasks = List.of(task1, task2);
      List<PlanSliceAggregate> slices = List.of(slice1);

      // When: 收集事件
      List<TaskQueuedEvent> collectedEvents = coordinator.collectQueuedEvents(tasks);

      // Then: 验证收集
      assertThat(collectedEvents).hasSize(2);

      // When: 发布事件
      coordinator.publishNewPlanEvents(collectedEvents, plan, schedule);

      // Then: 验证发布
      verify(taskOutboxPublisher).publish(collectedEvents, plan, schedule);

      // When: 构建结果
      PlanIngestionResult result = coordinator.buildIngestionResult(schedule, plan, slices, tasks);

      // Then: 验证结果
      assertThat(result.taskCount()).isEqualTo(2);
      assertThat(result.sliceIds()).hasSize(1);
    }

    @Test
    @DisplayName("完整流程：收集事件 → 发布重试 → 构建结果")
    void shouldExecuteCompleteRetryPublishingFlow() {
      // Given
      TaskQueuedEvent event1 = createTaskQueuedEvent(1L);

      when(task1.pullDomainEvents()).thenReturn(List.of(event1));
      when(plan.getId()).thenReturn(PlanId.of(100L));
      when(schedule.getId()).thenReturn(ScheduleInstanceId.of(1L));
      when(plan.getStatus()).thenReturn(PlanStatus.READY);
      when(slice1.getId()).thenReturn(PlanSliceId.of(10L));

      List<TaskAggregate> tasks = List.of(task1);
      List<PlanSliceAggregate> slices = List.of(slice1);

      // When: 收集事件
      List<TaskQueuedEvent> collectedEvents = coordinator.collectQueuedEvents(tasks);

      // Then: 验证收集
      assertThat(collectedEvents).hasSize(1);

      // When: 发布重试事件
      coordinator.publishRetryEvents(collectedEvents, plan, schedule);

      // Then: 验证发布
      verify(taskOutboxPublisher).publishRetry(collectedEvents, plan, schedule);

      // When: 构建结果
      PlanIngestionResult result = coordinator.buildIngestionResult(schedule, plan, slices, tasks);

      // Then: 验证结果
      assertThat(result.taskCount()).isEqualTo(1);
    }
  }

  // ==================== 辅助方法 ====================

  /// 创建 TaskQueuedEvent。
  private TaskQueuedEvent createTaskQueuedEvent(Long taskId) {
    return TaskQueuedEvent.of(
        taskId,
        100L,
        10L,
        1L,
        ProvenanceCode.PUBMED,
        "HARVEST",
        "task-key-" + taskId,
        "{}",
        1,
        Instant.now());
  }
}
