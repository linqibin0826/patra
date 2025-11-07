package com.patra.ingest.app.eventhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.patra.ingest.domain.event.SliceStatusChangedEvent;
import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.service.SliceStatusCalculator;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * TaskCompletedEventHandler 单元测试
 *
 * <p>测试策略: Mock 测试,验证事件处理的核心逻辑
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>正常场景: Task 完成 → Slice 状态更新 → 发布事件
 *   <li>幂等性: Slice 状态未变化,跳过更新
 *   <li>异常场景: Task 不存在、Slice 不存在
 *   <li>并发处理: 乐观锁冲突
 *   <li>异常处理: 通用异常捕获
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskCompletedEventHandler 单元测试")
class TaskCompletedEventHandlerTest {

  @Mock private TaskRepository taskRepository;

  @Mock private PlanSliceRepository sliceRepository;

  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private TaskCompletedEventHandler handler;

  private TaskCompletedEvent event;
  private TaskAggregate task;
  private PlanSliceAggregate slice;

  @BeforeEach
  void setUp() {
    // Given: 准备测试数据
    event = TaskCompletedEvent.of(100L, 200L, 300L, TaskStatus.SUCCEEDED.getCode(), Instant.now());

    // Mock TaskAggregate (lenient - 不是所有测试都使用)
    task = mock(TaskAggregate.class);
    lenient().when(task.getStatus()).thenReturn(TaskStatus.SUCCEEDED);

    // Mock PlanSliceAggregate (lenient - 不是所有测试都使用)
    slice = mock(PlanSliceAggregate.class);
    lenient().when(slice.getStatus()).thenReturn(SliceStatus.ASSIGNED);
  }

  @Test
  @DisplayName("应该成功处理 Task 完成事件并更新 Slice 状态")
  void shouldHandleTaskCompletedEventAndUpdateSliceStatus() {
    // Given: Task 和 Slice 都存在,状态需要变化
    when(taskRepository.findBySliceId(200L)).thenReturn(Optional.of(task));
    when(sliceRepository.findById(200L)).thenReturn(Optional.of(slice));

    // Mock static method: SliceStatusCalculator.calculate()
    try (MockedStatic<SliceStatusCalculator> mockedCalculator =
        mockStatic(SliceStatusCalculator.class)) {
      mockedCalculator
          .when(() -> SliceStatusCalculator.calculate(TaskStatus.SUCCEEDED))
          .thenReturn(SliceStatus.FINISHED);

      // When: 处理事件
      handler.handle(event);

      // Then: 验证 Slice 状态更新
      verify(slice).updateStatus(SliceStatus.FINISHED);
      verify(sliceRepository).save(slice);

      // Then: 验证发布 SliceStatusChangedEvent
      ArgumentCaptor<SliceStatusChangedEvent> eventCaptor =
          ArgumentCaptor.forClass(SliceStatusChangedEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());

      SliceStatusChangedEvent publishedEvent = eventCaptor.getValue();
      assertThat(publishedEvent.sliceId()).isEqualTo(200L);
      assertThat(publishedEvent.planId()).isEqualTo(300L);
      assertThat(publishedEvent.oldStatus()).isEqualTo(SliceStatus.ASSIGNED.getCode());
      assertThat(publishedEvent.newStatus()).isEqualTo(SliceStatus.FINISHED.getCode());
    }
  }

  @Test
  @DisplayName("当 Slice 状态未变化时应该跳过更新并且不发布事件")
  void shouldSkipUpdateWhenSliceStatusUnchanged() {
    // Given: Task 状态映射后的 Slice 状态与当前状态相同
    when(taskRepository.findBySliceId(200L)).thenReturn(Optional.of(task));
    when(sliceRepository.findById(200L)).thenReturn(Optional.of(slice));
    when(slice.getStatus()).thenReturn(SliceStatus.FINISHED);

    try (MockedStatic<SliceStatusCalculator> mockedCalculator =
        mockStatic(SliceStatusCalculator.class)) {
      mockedCalculator
          .when(() -> SliceStatusCalculator.calculate(TaskStatus.SUCCEEDED))
          .thenReturn(SliceStatus.FINISHED);

      // When: 处理事件
      handler.handle(event);

      // Then: 不应该更新 Slice 状态
      verify(slice, never()).updateStatus(any());
      verify(sliceRepository, never()).save(any());

      // Then: 不应该发布事件
      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Test
  @DisplayName("当 Task 不存在时应该记录警告并跳过更新")
  void shouldLogWarningWhenTaskNotFound() {
    // Given: Task 不存在
    when(taskRepository.findBySliceId(200L)).thenReturn(Optional.empty());

    // When: 处理事件
    handler.handle(event);

    // Then: 不应该查询 Slice
    verify(sliceRepository, never()).findById(any());

    // Then: 不应该发布事件
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("当 Slice 不存在时应该抛出 IllegalStateException")
  void shouldThrowExceptionWhenSliceNotFound() {
    // Given: Task 存在但 Slice 不存在
    when(taskRepository.findBySliceId(200L)).thenReturn(Optional.of(task));
    when(sliceRepository.findById(200L)).thenReturn(Optional.empty());

    try (MockedStatic<SliceStatusCalculator> mockedCalculator =
        mockStatic(SliceStatusCalculator.class)) {
      mockedCalculator
          .when(() -> SliceStatusCalculator.calculate(TaskStatus.SUCCEEDED))
          .thenReturn(SliceStatus.FINISHED);

      // When: 处理事件
      handler.handle(event);

      // Then: 异常被捕获,不应该发布事件
      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Test
  @DisplayName("当发生乐观锁冲突时应该记录警告并跳过更新")
  void shouldLogWarningOnOptimisticLockingFailure() {
    // Given: Task 和 Slice 都存在
    when(taskRepository.findBySliceId(200L)).thenReturn(Optional.of(task));
    when(sliceRepository.findById(200L)).thenReturn(Optional.of(slice));

    // Given: 保存 Slice 时发生乐观锁冲突
    when(sliceRepository.save(any())).thenThrow(new OptimisticLockingFailureException("冲突"));

    try (MockedStatic<SliceStatusCalculator> mockedCalculator =
        mockStatic(SliceStatusCalculator.class)) {
      mockedCalculator
          .when(() -> SliceStatusCalculator.calculate(TaskStatus.SUCCEEDED))
          .thenReturn(SliceStatus.FINISHED);

      // When: 处理事件
      handler.handle(event);

      // Then: 乐观锁异常被捕获,不应该发布事件
      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Test
  @DisplayName("当发生其他异常时应该记录错误并跳过更新")
  void shouldLogErrorOnGenericException() {
    // Given: Task 查询时抛出异常
    when(taskRepository.findBySliceId(200L)).thenThrow(new RuntimeException("数据库错误"));

    // When: 处理事件
    handler.handle(event);

    // Then: 异常被捕获,不应该发布事件
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("应该正确处理 Task FAILED 状态并映射到 Slice FINISHED 状态")
  void shouldHandleTaskFailedStatusCorrectly() {
    // Given: Task 失败
    TaskCompletedEvent failedEvent =
        TaskCompletedEvent.ofFailure(
            100L, 200L, 300L, TaskStatus.FAILED.getCode(), "ERR001", "任务失败", Instant.now());
    when(task.getStatus()).thenReturn(TaskStatus.FAILED);
    when(taskRepository.findBySliceId(200L)).thenReturn(Optional.of(task));
    when(sliceRepository.findById(200L)).thenReturn(Optional.of(slice));

    try (MockedStatic<SliceStatusCalculator> mockedCalculator =
        mockStatic(SliceStatusCalculator.class)) {
      mockedCalculator
          .when(() -> SliceStatusCalculator.calculate(TaskStatus.FAILED))
          .thenReturn(SliceStatus.FINISHED);

      // When: 处理失败事件
      handler.handle(failedEvent);

      // Then: 验证 Slice 状态更新为 FINISHED
      verify(slice).updateStatus(SliceStatus.FINISHED);
      verify(sliceRepository).save(slice);

      // Then: 验证发布事件
      ArgumentCaptor<SliceStatusChangedEvent> eventCaptor =
          ArgumentCaptor.forClass(SliceStatusChangedEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());

      SliceStatusChangedEvent publishedEvent = eventCaptor.getValue();
      assertThat(publishedEvent.newStatus()).isEqualTo(SliceStatus.FINISHED.getCode());
    }
  }

  @Test
  @DisplayName("应该正确处理 Task RUNNING 状态并映射到 Slice ASSIGNED 状态")
  void shouldHandleTaskRunningStatusCorrectly() {
    // Given: Task 运行中
    TaskCompletedEvent runningEvent =
        TaskCompletedEvent.of(100L, 200L, 300L, TaskStatus.RUNNING.getCode(), Instant.now());
    when(task.getStatus()).thenReturn(TaskStatus.RUNNING);
    when(taskRepository.findBySliceId(200L)).thenReturn(Optional.of(task));
    when(sliceRepository.findById(200L)).thenReturn(Optional.of(slice));
    when(slice.getStatus()).thenReturn(SliceStatus.PENDING);

    try (MockedStatic<SliceStatusCalculator> mockedCalculator =
        mockStatic(SliceStatusCalculator.class)) {
      mockedCalculator
          .when(() -> SliceStatusCalculator.calculate(TaskStatus.RUNNING))
          .thenReturn(SliceStatus.ASSIGNED);

      // When: 处理运行中事件
      handler.handle(runningEvent);

      // Then: 验证 Slice 状态更新为 ASSIGNED
      verify(slice).updateStatus(SliceStatus.ASSIGNED);
      verify(sliceRepository).save(slice);

      // Then: 验证发布事件
      verify(eventPublisher).publishEvent(any(SliceStatusChangedEvent.class));
    }
  }
}
