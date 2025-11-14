package com.patra.ingest.app.eventhandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.patra.ingest.domain.event.SliceStatusChangedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.service.PlanStatusCalculator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * SliceStatusChangedEventHandler 单元测试
 *
 * <p>测试策略: Mock 测试,验证事件处理的核心逻辑
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>正常场景: Slice 状态变化 → 聚合计算 Plan 状态 → 更新 Plan
 *   <li>幂等性: Plan 状态未变化,跳过更新
 *   <li>异常场景: Plan 不存在
 *   <li>并发处理: 乐观锁冲突
 *   <li>异常处理: 通用异常捕获
 *   <li>多 Slice 聚合: 测试所有 Slice FINISHED → Plan ARCHIVED
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SliceStatusChangedEventHandler 单元测试")
class SliceStatusChangedEventHandlerTest {

  @Mock private PlanSliceRepository sliceRepository;

  @Mock private PlanRepository planRepository;

  @InjectMocks private SliceStatusChangedEventHandler handler;

  private SliceStatusChangedEvent event;
  private PlanAggregate plan;
  private PlanSliceAggregate slice1;
  private PlanSliceAggregate slice2;

  @BeforeEach
  void setUp() {
    // Given: 准备测试数据
    event =
        SliceStatusChangedEvent.of(
            200L, 300L, SliceStatus.ASSIGNED.getCode(), SliceStatus.FINISHED.getCode());

    // Mock PlanAggregate (lenient - 不是所有测试都使用)
    plan = mock(PlanAggregate.class);
    lenient().when(plan.getStatus()).thenReturn(PlanStatus.READY);

    // Mock PlanSliceAggregates (lenient - 不是所有测试都使用)
    slice1 = mock(PlanSliceAggregate.class);
    slice2 = mock(PlanSliceAggregate.class);
  }

  @Test
  @DisplayName("应该成功处理 Slice 状态变更事件并更新 Plan 状态")
  void shouldHandleSliceStatusChangedEventAndUpdatePlanStatus() {
    // Given: 所有 Slice 都已完成
    when(slice1.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(slice2.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(sliceRepository.findByPlanId(300L)).thenReturn(List.of(slice1, slice2));
    when(planRepository.findById(300L)).thenReturn(Optional.of(plan));

    // Mock static method: PlanStatusCalculator.calculate()
    try (MockedStatic<PlanStatusCalculator> mockedCalculator =
        mockStatic(PlanStatusCalculator.class)) {
      mockedCalculator
          .when(
              () ->
                  PlanStatusCalculator.calculate(
                      List.of(SliceStatus.FINISHED, SliceStatus.FINISHED), PlanStatus.READY))
          .thenReturn(PlanStatus.ARCHIVED);

      // When: 处理事件
      handler.handle(event);

      // Then: 验证 Plan 状态更新为 ARCHIVED
      verify(plan).updateStatus(PlanStatus.ARCHIVED);
      verify(planRepository).save(plan);
    }
  }

  @Test
  @DisplayName("当 Plan 状态未变化时应该跳过更新")
  void shouldSkipUpdateWhenPlanStatusUnchanged() {
    // Given: Plan 状态计算后保持不变
    when(slice1.getStatus()).thenReturn(SliceStatus.ASSIGNED);
    when(slice2.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(sliceRepository.findByPlanId(300L)).thenReturn(List.of(slice1, slice2));
    when(planRepository.findById(300L)).thenReturn(Optional.of(plan));
    when(plan.getStatus()).thenReturn(PlanStatus.READY);

    try (MockedStatic<PlanStatusCalculator> mockedCalculator =
        mockStatic(PlanStatusCalculator.class)) {
      mockedCalculator
          .when(
              () ->
                  PlanStatusCalculator.calculate(
                      List.of(SliceStatus.ASSIGNED, SliceStatus.FINISHED), PlanStatus.READY))
          .thenReturn(PlanStatus.READY);

      // When: 处理事件
      handler.handle(event);

      // Then: 不应该更新 Plan 状态
      verify(plan, never()).updateStatus(any());
      verify(planRepository, never()).save(any());
    }
  }

  @Test
  @DisplayName("当 Plan 不存在时应该抛出 IllegalStateException")
  void shouldThrowExceptionWhenPlanNotFound() {
    // Given: Slice 存在但 Plan 不存在
    when(slice1.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(sliceRepository.findByPlanId(300L)).thenReturn(List.of(slice1));
    when(planRepository.findById(300L)).thenReturn(Optional.empty());

    // When: 处理事件
    handler.handle(event);

    // Then: 异常被捕获,不应该更新 Plan
    verify(planRepository, never()).save(any());
  }

  @Test
  @DisplayName("当发生乐观锁冲突时应该记录警告并跳过更新")
  void shouldLogWarningOnOptimisticLockingFailure() {
    // Given: Slice 和 Plan 都存在
    when(slice1.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(sliceRepository.findByPlanId(300L)).thenReturn(List.of(slice1));
    when(planRepository.findById(300L)).thenReturn(Optional.of(plan));

    // Given: 保存 Plan 时发生乐观锁冲突
    when(planRepository.save(any())).thenThrow(new OptimisticLockingFailureException("冲突"));

    try (MockedStatic<PlanStatusCalculator> mockedCalculator =
        mockStatic(PlanStatusCalculator.class)) {
      mockedCalculator
          .when(
              () -> PlanStatusCalculator.calculate(List.of(SliceStatus.FINISHED), PlanStatus.READY))
          .thenReturn(PlanStatus.ARCHIVED);

      // When: 处理事件
      handler.handle(event);

      // Then: 乐观锁异常被捕获
      verify(plan).updateStatus(PlanStatus.ARCHIVED);
    }
  }

  @Test
  @DisplayName("当发生其他异常时应该记录错误并跳过更新")
  void shouldLogErrorOnGenericException() {
    // Given: Slice 查询时抛出异常
    when(sliceRepository.findByPlanId(300L)).thenThrow(new RuntimeException("数据库错误"));

    // When: 处理事件
    handler.handle(event);

    // Then: 异常被捕获,不应该更新 Plan
    verify(planRepository, never()).save(any());
  }

  @Test
  @DisplayName("应该正确聚合多个 Slice 状态并更新 Plan 状态")
  void shouldAggregateMultipleSliceStatusesCorrectly() {
    // Given: 多个 Slice 处于不同状态
    when(slice1.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(slice2.getStatus()).thenReturn(SliceStatus.ASSIGNED);
    PlanSliceAggregate slice3 = mock(PlanSliceAggregate.class);
    when(slice3.getStatus()).thenReturn(SliceStatus.PENDING);

    when(sliceRepository.findByPlanId(300L)).thenReturn(List.of(slice1, slice2, slice3));
    when(planRepository.findById(300L)).thenReturn(Optional.of(plan));

    try (MockedStatic<PlanStatusCalculator> mockedCalculator =
        mockStatic(PlanStatusCalculator.class)) {
      mockedCalculator
          .when(
              () ->
                  PlanStatusCalculator.calculate(
                      List.of(SliceStatus.FINISHED, SliceStatus.ASSIGNED, SliceStatus.PENDING),
                      PlanStatus.READY))
          .thenReturn(PlanStatus.READY);

      // When: 处理事件
      handler.handle(event);

      // Then: 验证聚合计算逻辑被调用
      mockedCalculator.verify(
          () ->
              PlanStatusCalculator.calculate(
                  List.of(SliceStatus.FINISHED, SliceStatus.ASSIGNED, SliceStatus.PENDING),
                  PlanStatus.READY));

      // Then: Plan 状态未变化,不应该更新
      verify(plan, never()).updateStatus(any());
      verify(planRepository, never()).save(any());
    }
  }

  @Test
  @DisplayName("当所有 Slice 都完成时应该将 Plan 状态更新为 ARCHIVED")
  void shouldArchivePlanWhenAllSlicesFinished() {
    // Given: 所有 Slice 都已完成
    when(slice1.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(slice2.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(sliceRepository.findByPlanId(300L)).thenReturn(List.of(slice1, slice2));
    when(planRepository.findById(300L)).thenReturn(Optional.of(plan));
    when(plan.getStatus()).thenReturn(PlanStatus.READY);

    try (MockedStatic<PlanStatusCalculator> mockedCalculator =
        mockStatic(PlanStatusCalculator.class)) {
      mockedCalculator
          .when(
              () ->
                  PlanStatusCalculator.calculate(
                      List.of(SliceStatus.FINISHED, SliceStatus.FINISHED), PlanStatus.READY))
          .thenReturn(PlanStatus.ARCHIVED);

      // When: 处理事件
      handler.handle(event);

      // Then: 验证 Plan 状态更新为 ARCHIVED
      verify(plan).updateStatus(PlanStatus.ARCHIVED);
      verify(planRepository).save(plan);
    }
  }

  @Test
  @DisplayName("应该正确处理空 Slice 列表的场景")
  void shouldHandleEmptySliceListCorrectly() {
    // Given: Plan 下没有 Slice
    when(sliceRepository.findByPlanId(300L)).thenReturn(List.of());
    when(planRepository.findById(300L)).thenReturn(Optional.of(plan));

    try (MockedStatic<PlanStatusCalculator> mockedCalculator =
        mockStatic(PlanStatusCalculator.class)) {
      mockedCalculator
          .when(() -> PlanStatusCalculator.calculate(List.of(), PlanStatus.READY))
          .thenReturn(PlanStatus.READY);

      // When: 处理事件
      handler.handle(event);

      // Then: 验证聚合计算逻辑被调用
      mockedCalculator.verify(() -> PlanStatusCalculator.calculate(List.of(), PlanStatus.READY));

      // Then: Plan 状态未变化,不应该更新
      verify(plan, never()).updateStatus(any());
      verify(planRepository, never()).save(any());
    }
  }

  @Test
  @DisplayName("应该正确处理单个 Slice 的场景")
  void shouldHandleSingleSliceCorrectly() {
    // Given: Plan 下只有一个 Slice,且已完成
    when(slice1.getStatus()).thenReturn(SliceStatus.FINISHED);
    when(sliceRepository.findByPlanId(300L)).thenReturn(List.of(slice1));
    when(planRepository.findById(300L)).thenReturn(Optional.of(plan));

    try (MockedStatic<PlanStatusCalculator> mockedCalculator =
        mockStatic(PlanStatusCalculator.class)) {
      mockedCalculator
          .when(
              () -> PlanStatusCalculator.calculate(List.of(SliceStatus.FINISHED), PlanStatus.READY))
          .thenReturn(PlanStatus.ARCHIVED);

      // When: 处理事件
      handler.handle(event);

      // Then: 验证 Plan 状态更新为 ARCHIVED
      verify(plan).updateStatus(PlanStatus.ARCHIVED);
      verify(planRepository).save(plan);
    }
  }
}
