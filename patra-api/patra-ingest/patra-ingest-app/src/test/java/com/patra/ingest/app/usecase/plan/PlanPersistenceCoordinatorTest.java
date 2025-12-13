package com.patra.ingest.app.usecase.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.slice.PlanSliceId;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

/// PlanPersistenceCoordinator 单元测试。
///
/// 测试覆盖:
///
/// - ✅ persistScheduleInstance() - 正常持久化调度实例
///   - ✅ persistScheduleInstance() - 持久化失败异常包装
///   - ✅ savePlan() - 正常持久化计划
///   - ✅ savePlan() - 持久化失败异常包装
///   - ✅ persistSlices() - 正常批量持久化切片
///   - ✅ persistSlices() - 空切片列表处理
///   - ✅ persistSlices() - 切片绑定计划 ID 验证
///   - ✅ persistTasks() - 正常批量持久化任务
///   - ✅ persistTasks() - 空任务列表处理
///   - ✅ persistTasks() - 任务绑定计划和切片 ID 验证
///   - ✅ saveTask() - 正常持久化单个任务
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlanPersistenceCoordinator 单元测试")
class PlanPersistenceCoordinatorTest {

  @Mock private PlanRepository planRepository;
  @Mock private PlanSliceRepository planSliceRepository;
  @Mock private TaskRepository taskRepository;
  @Mock private ScheduleInstanceRepository scheduleInstanceRepository;

  @Mock private PlanAggregate plan;
  @Mock private PlanSliceAggregate slice1;
  @Mock private PlanSliceAggregate slice2;
  @Mock private TaskAggregate task1;
  @Mock private TaskAggregate task2;
  @Mock private ScheduleInstanceAggregate schedule;

  @Captor private ArgumentCaptor<List<PlanSliceAggregate>> slicesCaptor;
  @Captor private ArgumentCaptor<List<TaskAggregate>> tasksCaptor;

  private PlanPersistenceCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator =
        new PlanPersistenceCoordinator(
            planRepository, planSliceRepository, taskRepository, scheduleInstanceRepository);
  }

  @Nested
  @DisplayName("persistScheduleInstance() 测试")
  class PersistScheduleInstanceTests {

    @Test
    @DisplayName("应成功持久化调度实例")
    void shouldPersistScheduleInstanceSuccessfully() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      ScheduleInstanceAggregate expectedSchedule = schedule;

      when(scheduleInstanceRepository.saveOrUpdateInstance(any(ScheduleInstanceAggregate.class)))
          .thenReturn(expectedSchedule);

      // When
      ScheduleInstanceAggregate result = coordinator.persistScheduleInstance(command);

      // Then
      assertThat(result).isEqualTo(expectedSchedule);
      verify(scheduleInstanceRepository).saveOrUpdateInstance(any(ScheduleInstanceAggregate.class));
    }

    @Test
    @DisplayName("当持久化失败时应抛出 PlanPersistenceException")
    void shouldThrowPlanPersistenceExceptionWhenPersistenceFails() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      RuntimeException cause = new RuntimeException("Database connection failed");

      when(scheduleInstanceRepository.saveOrUpdateInstance(any())).thenThrow(cause);

      // When & Then
      assertThatThrownBy(() -> coordinator.persistScheduleInstance(command))
          .isInstanceOf(PlanPersistenceException.class)
          .hasMessageContaining("持久化调度实例失败")
          .hasCause(cause);

      assertThatThrownBy(() -> coordinator.persistScheduleInstance(command))
          .extracting(ex -> ((PlanPersistenceException) ex).getStage())
          .isEqualTo(PlanPersistenceException.Stage.SCHEDULE_INSTANCE);
    }

    @Test
    @DisplayName("应正确传递调度实例属性")
    void shouldPassCorrectScheduleInstanceAttributes() {
      // Given
      PlanIngestionCommand command = createValidCommand();

      when(scheduleInstanceRepository.saveOrUpdateInstance(any())).thenReturn(schedule);

      // When
      coordinator.persistScheduleInstance(command);

      // Then
      ArgumentCaptor<ScheduleInstanceAggregate> scheduleCaptor =
          ArgumentCaptor.forClass(ScheduleInstanceAggregate.class);
      verify(scheduleInstanceRepository).saveOrUpdateInstance(scheduleCaptor.capture());

      ScheduleInstanceAggregate capturedSchedule = scheduleCaptor.getValue();
      assertThat(capturedSchedule).isNotNull();
    }
  }

  @Nested
  @DisplayName("savePlan() 测试")
  class SavePlanTests {

    @Test
    @DisplayName("应成功持久化计划聚合根")
    void shouldSavePlanSuccessfully() {
      // Given
      PlanAggregate draftPlan = plan;
      PlanAggregate persistedPlan = plan;

      when(planRepository.save(draftPlan)).thenReturn(persistedPlan);

      // When
      PlanAggregate result = coordinator.savePlan(draftPlan);

      // Then
      assertThat(result).isEqualTo(persistedPlan);
      verify(planRepository).save(draftPlan);
    }

    @Test
    @DisplayName("当持久化失败时应抛出 PlanPersistenceException")
    void shouldThrowPlanPersistenceExceptionWhenSaveFails() {
      // Given
      RuntimeException cause = new RuntimeException("Unique constraint violation");

      when(planRepository.save(any())).thenThrow(cause);

      // When & Then
      assertThatThrownBy(() -> coordinator.savePlan(plan))
          .isInstanceOf(PlanPersistenceException.class)
          .hasMessageContaining("持久化计划聚合根失败")
          .hasCause(cause);

      assertThatThrownBy(() -> coordinator.savePlan(plan))
          .extracting(ex -> ((PlanPersistenceException) ex).getStage())
          .isEqualTo(PlanPersistenceException.Stage.PLAN);
    }
  }

  @Nested
  @DisplayName("persistSlices() 测试")
  class PersistSlicesTests {

    @Test
    @DisplayName("应成功批量持久化切片")
    void shouldPersistSlicesSuccessfully() {
      // Given
      PlanId planId = PlanId.of(100L);
      when(plan.getId()).thenReturn(planId);

      List<PlanSliceAggregate> slices = List.of(slice1, slice2);
      List<PlanSliceAggregate> persistedSlices = List.of(slice1, slice2);

      when(planSliceRepository.saveAll(anyList())).thenReturn(persistedSlices);

      // When
      List<PlanSliceAggregate> result = coordinator.persistSlices(plan, slices);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).isEqualTo(persistedSlices);

      // 验证切片绑定了计划 ID
      verify(slice1).bindPlan(planId);
      verify(slice2).bindPlan(planId);
      verify(planSliceRepository).saveAll(slices);
    }

    @Test
    @DisplayName("当切片列表为空时应返回空列表")
    void shouldReturnEmptyListWhenSlicesAreEmpty() {
      // Given
      List<PlanSliceAggregate> emptySlices = Collections.emptyList();

      // When
      List<PlanSliceAggregate> result = coordinator.persistSlices(plan, emptySlices);

      // Then
      assertThat(result).isEmpty();
      verify(planSliceRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("当切片列表为 null 时应返回空列表")
    void shouldReturnEmptyListWhenSlicesAreNull() {
      // Given
      List<PlanSliceAggregate> nullSlices = null;

      // When
      List<PlanSliceAggregate> result = coordinator.persistSlices(plan, nullSlices);

      // Then
      assertThat(result).isEmpty();
      verify(planSliceRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("当持久化失败时应抛出 PlanPersistenceException")
    void shouldThrowPlanPersistenceExceptionWhenPersistenceFails() {
      // Given
      PlanId planId = PlanId.of(100L);
      when(plan.getId()).thenReturn(planId);

      List<PlanSliceAggregate> slices = List.of(slice1);
      RuntimeException cause = new RuntimeException("Database error");

      when(planSliceRepository.saveAll(anyList())).thenThrow(cause);

      // When & Then
      assertThatThrownBy(() -> coordinator.persistSlices(plan, slices))
          .isInstanceOf(PlanPersistenceException.class)
          .hasMessageContaining("持久化计划切片失败")
          .hasCause(cause);

      assertThatThrownBy(() -> coordinator.persistSlices(plan, slices))
          .extracting(ex -> ((PlanPersistenceException) ex).getStage())
          .isEqualTo(PlanPersistenceException.Stage.PLAN_SLICE);
    }

    @Test
    @DisplayName("应正确处理多个切片的批量持久化")
    void shouldHandleMultipleSlicesPersistence() {
      // Given
      PlanId planId = PlanId.of(100L);
      when(plan.getId()).thenReturn(planId);

      PlanSliceAggregate slice3 = org.mockito.Mockito.mock(PlanSliceAggregate.class);
      PlanSliceAggregate slice4 = org.mockito.Mockito.mock(PlanSliceAggregate.class);
      List<PlanSliceAggregate> slices = List.of(slice1, slice2, slice3, slice4);

      when(planSliceRepository.saveAll(anyList())).thenReturn(slices);

      // When
      List<PlanSliceAggregate> result = coordinator.persistSlices(plan, slices);

      // Then
      assertThat(result).hasSize(4);
      verify(slice1).bindPlan(planId);
      verify(slice2).bindPlan(planId);
      verify(slice3).bindPlan(planId);
      verify(slice4).bindPlan(planId);
    }
  }

  @Nested
  @DisplayName("persistTasks() 测试")
  class PersistTasksTests {

    @Test
    @DisplayName("应成功批量持久化任务并绑定计划和切片 ID")
    void shouldPersistTasksSuccessfully() {
      // Given
      PlanId planId = PlanId.of(100L);
      PlanSliceId sliceId1 = PlanSliceId.of(10L);
      PlanSliceId sliceId2 = PlanSliceId.of(20L);

      when(plan.getId()).thenReturn(planId);
      when(slice1.getId()).thenReturn(sliceId1);
      when(slice1.getSliceNo()).thenReturn(1);
      when(slice2.getId()).thenReturn(sliceId2);
      when(slice2.getSliceNo()).thenReturn(2);

      // 任务的 sliceNo 通过 paramsJson 传递
      when(task1.getParamsJson()).thenReturn("{\"sliceNo\":1}"); // 对应 slice1
      when(task2.getParamsJson()).thenReturn("{\"sliceNo\":2}"); // 对应 slice2

      List<PlanSliceAggregate> persistedSlices = List.of(slice1, slice2);
      List<TaskAggregate> tasks = List.of(task1, task2);
      List<TaskAggregate> persistedTasks = List.of(task1, task2);

      when(taskRepository.saveAll(anyList())).thenReturn(persistedTasks);

      // When
      List<TaskAggregate> result = coordinator.persistTasks(plan, persistedSlices, tasks);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).isEqualTo(persistedTasks);

      // 验证任务绑定了计划 ID 和切片 ID
      verify(task1).bindPlanAndSlice(planId, sliceId1);
      verify(task2).bindPlanAndSlice(planId, sliceId2);
      verify(taskRepository).saveAll(tasks);
    }

    @Test
    @DisplayName("当任务列表为空时应返回空列表")
    void shouldReturnEmptyListWhenTasksAreEmpty() {
      // Given
      List<TaskAggregate> emptyTasks = Collections.emptyList();
      List<PlanSliceAggregate> slices = List.of(slice1);

      // When
      List<TaskAggregate> result = coordinator.persistTasks(plan, slices, emptyTasks);

      // Then
      assertThat(result).isEmpty();
      verify(taskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("当任务列表为 null 时应返回空列表")
    void shouldReturnEmptyListWhenTasksAreNull() {
      // Given
      List<TaskAggregate> nullTasks = null;
      List<PlanSliceAggregate> slices = List.of(slice1);

      // When
      List<TaskAggregate> result = coordinator.persistTasks(plan, slices, nullTasks);

      // Then
      assertThat(result).isEmpty();
      verify(taskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("当持久化失败时应抛出 PlanPersistenceException")
    void shouldThrowPlanPersistenceExceptionWhenPersistenceFails() {
      // Given
      PlanId planId = PlanId.of(100L);
      PlanSliceId sliceId = PlanSliceId.of(10L);

      when(plan.getId()).thenReturn(planId);
      when(slice1.getId()).thenReturn(sliceId);
      when(slice1.getSliceNo()).thenReturn(1);
      when(task1.getParamsJson()).thenReturn("{\"sliceNo\":1}");

      List<PlanSliceAggregate> slices = List.of(slice1);
      List<TaskAggregate> tasks = List.of(task1);

      RuntimeException cause = new RuntimeException("Database error");
      when(taskRepository.saveAll(anyList())).thenThrow(cause);

      // When & Then
      assertThatThrownBy(() -> coordinator.persistTasks(plan, slices, tasks))
          .isInstanceOf(PlanPersistenceException.class)
          .hasMessageContaining("持久化任务失败")
          .hasCause(cause);

      assertThatThrownBy(() -> coordinator.persistTasks(plan, slices, tasks))
          .extracting(ex -> ((PlanPersistenceException) ex).getStage())
          .isEqualTo(PlanPersistenceException.Stage.TASK);
    }

    @Test
    @DisplayName("应正确处理任务的切片序号映射")
    void shouldHandleSliceSequenceMapping() {
      // Given
      PlanId planId = PlanId.of(100L);
      PlanSliceId sliceId1 = PlanSliceId.of(10L);
      PlanSliceId sliceId2 = PlanSliceId.of(20L);

      when(plan.getId()).thenReturn(planId);
      when(slice1.getId()).thenReturn(sliceId1);
      when(slice1.getSliceNo()).thenReturn(1);
      when(slice2.getId()).thenReturn(sliceId2);
      when(slice2.getSliceNo()).thenReturn(2);

      // 任务 1 和 2 都关联到 slice1 (序号 1)
      when(task1.getParamsJson()).thenReturn("{\"sliceNo\":1}");
      when(task2.getParamsJson()).thenReturn("{\"sliceNo\":1}");

      List<PlanSliceAggregate> slices = List.of(slice1, slice2);
      List<TaskAggregate> tasks = List.of(task1, task2);

      when(taskRepository.saveAll(anyList())).thenReturn(tasks);

      // When
      coordinator.persistTasks(plan, slices, tasks);

      // Then
      verify(task1).bindPlanAndSlice(planId, sliceId1);
      verify(task2).bindPlanAndSlice(planId, sliceId1);
    }

    @Test
    @DisplayName("应处理任务没有关联切片的情况")
    void shouldHandleTasksWithoutSlice() {
      // Given
      PlanId planId = PlanId.of(100L);

      when(plan.getId()).thenReturn(planId);
      when(task1.getParamsJson()).thenReturn(null); // 无 paramsJson，无法解析 sliceNo

      List<PlanSliceAggregate> slices = List.of(slice1);
      List<TaskAggregate> tasks = List.of(task1);

      when(taskRepository.saveAll(anyList())).thenReturn(tasks);

      // When
      coordinator.persistTasks(plan, slices, tasks);

      // Then
      verify(task1).bindPlanAndSlice(planId, null);
    }
  }

  @Nested
  @DisplayName("saveTask() 测试")
  class SaveTaskTests {

    @Test
    @DisplayName("应成功持久化单个任务")
    void shouldSaveTaskSuccessfully() {
      // Given
      TaskAggregate task = task1;

      // When
      coordinator.saveTask(task);

      // Then
      verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("当持久化失败时应抛出 PlanPersistenceException")
    void shouldThrowPlanPersistenceExceptionWhenSaveFails() {
      // Given
      RuntimeException cause = new RuntimeException("Optimistic lock failed");

      when(taskRepository.save(any())).thenThrow(cause);

      // When & Then
      assertThatThrownBy(() -> coordinator.saveTask(task1))
          .isInstanceOf(PlanPersistenceException.class)
          .hasMessageContaining("持久化任务重试状态失败")
          .hasCause(cause);

      assertThatThrownBy(() -> coordinator.saveTask(task1))
          .extracting(ex -> ((PlanPersistenceException) ex).getStage())
          .isEqualTo(PlanPersistenceException.Stage.TASK_RETRY);
    }

    @Test
    @DisplayName("应支持多次调用持久化不同任务")
    void shouldSupportMultipleSaveCalls() {
      // Given
      // 使用已定义的 Mock 字段

      // When
      coordinator.saveTask(task1);
      coordinator.saveTask(task2);

      // Then
      verify(taskRepository, times(1)).save(task1);
      verify(taskRepository, times(1)).save(task2);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryTests {

    @Test
    @DisplayName("应处理空切片列表和空任务列表")
    void shouldHandleEmptySlicesAndTasks() {
      // Given
      List<PlanSliceAggregate> emptySlices = Collections.emptyList();
      List<TaskAggregate> emptyTasks = Collections.emptyList();

      // When
      List<PlanSliceAggregate> slicesResult = coordinator.persistSlices(plan, emptySlices);
      List<TaskAggregate> tasksResult = coordinator.persistTasks(plan, emptySlices, emptyTasks);

      // Then
      assertThat(slicesResult).isEmpty();
      assertThat(tasksResult).isEmpty();
      verify(planSliceRepository, never()).saveAll(anyList());
      verify(taskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("应正确处理大批量切片持久化")
    void shouldHandleLargeBatchOfSlices() {
      // Given
      PlanId planId = PlanId.of(100L);
      when(plan.getId()).thenReturn(planId);

      List<PlanSliceAggregate> largeSliceList = new java.util.ArrayList<>();
      for (int i = 0; i < 100; i++) {
        PlanSliceAggregate slice = org.mockito.Mockito.mock(PlanSliceAggregate.class);
        largeSliceList.add(slice);
      }

      when(planSliceRepository.saveAll(anyList())).thenReturn(largeSliceList);

      // When
      List<PlanSliceAggregate> result = coordinator.persistSlices(plan, largeSliceList);

      // Then
      assertThat(result).hasSize(100);
      verify(planSliceRepository).saveAll(slicesCaptor.capture());
      assertThat(slicesCaptor.getValue()).hasSize(100);
    }

    @Test
    @DisplayName("应正确处理大批量任务持久化")
    void shouldHandleLargeBatchOfTasks() {
      // Given
      PlanId planId = PlanId.of(100L);
      PlanSliceId sliceId = PlanSliceId.of(10L);

      when(plan.getId()).thenReturn(planId);
      when(slice1.getId()).thenReturn(sliceId);
      when(slice1.getSliceNo()).thenReturn(1);

      List<TaskAggregate> largeTaskList = new java.util.ArrayList<>();
      for (int i = 0; i < 100; i++) {
        TaskAggregate task = org.mockito.Mockito.mock(TaskAggregate.class);
        when(task.getParamsJson()).thenReturn("{\"sliceNo\":1}");
        largeTaskList.add(task);
      }

      when(taskRepository.saveAll(anyList())).thenReturn(largeTaskList);

      // When
      List<TaskAggregate> result = coordinator.persistTasks(plan, List.of(slice1), largeTaskList);

      // Then
      assertThat(result).hasSize(100);
      verify(taskRepository).saveAll(tasksCaptor.capture());
      assertThat(tasksCaptor.getValue()).hasSize(100);
    }
  }

  // ==================== 辅助方法 ====================

  /// 创建有效的 PlanIngestionCommand。
  private PlanIngestionCommand createValidCommand() {
    return new PlanIngestionCommand(
        ProvenanceCode.PUBMED,
        OperationCode.HARVEST,
        "PT1H",
        TriggerType.MANUAL,
        Scheduler.XXL,
        "job-001",
        "log-001",
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-01-01T01:00:00Z"),
        Priority.NORMAL,
        Instant.now(),
        Map.of());
  }
}
