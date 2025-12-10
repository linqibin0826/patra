package com.patra.ingest.app.usecase.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.plan.assembler.PlanAssembler;
import com.patra.ingest.app.usecase.plan.assembler.PlanAssemblyRequest;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionBuilder;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.validator.PlannerValidator;
import com.patra.ingest.app.usecase.plan.window.PlanningWindowResolver;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.exception.PlanAssemblyException;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import com.patra.ingest.domain.port.CursorRepository;
import com.patra.ingest.domain.port.PatraRegistryPort;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.TaskRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/// PlanIngestionHandler 单元测试。
///
/// 测试覆盖:
///
/// - ✅ handle() - 正常流程，创建新计划
/// - ✅ handle() - 幂等性场景，复用现有计划
/// - ✅ handle() - 游标水位查询失败
/// - ✅ handle() - 窗口解析失败
/// - ✅ handle() - 预验证失败
/// - ✅ handle() - 组装失败
/// - ✅ handle() - 持久化失败
/// - ✅ 调用顺序验证
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlanIngestionHandler 单元测试")
class PlanIngestionHandlerTest {

  @Mock private PatraRegistryPort patraRegistryPort;
  @Mock private CursorRepository cursorRepository;
  @Mock private TaskRepository taskRepository;
  @Mock private PlanningWindowResolver planningWindowResolver;
  @Mock private PlannerValidator plannerValidator;
  @Mock private PlanAssembler planAssembler;
  @Mock private PlanExpressionBuilder planExpressionBuilder;
  @Mock private PlanRepository planRepository;
  @Mock private PlanPersistenceCoordinator persistenceCoordinator;
  @Mock private PlanIdempotencyCoordinator idempotencyCoordinator;
  @Mock private PlanPublishingCoordinator publishingCoordinator;

  @Mock private ScheduleInstanceAggregate schedule;
  @Mock private ProvenanceConfigSnapshot configSnapshot;
  @Mock private PlannerWindow window;
  @Mock private PlanExpressionDescriptor expressionDescriptor;
  @Mock private PlanAggregate plan;
  @Mock private PlanSliceAggregate slice;
  @Mock private TaskAggregate task;

  @Captor private ArgumentCaptor<PlanAssemblyRequest> assemblyRequestCaptor;

  private PlanIngestionHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new PlanIngestionHandler(
            patraRegistryPort,
            cursorRepository,
            taskRepository,
            planningWindowResolver,
            plannerValidator,
            planAssembler,
            planExpressionBuilder,
            planRepository,
            persistenceCoordinator,
            idempotencyCoordinator,
            publishingCoordinator);
  }

  @Nested
  @DisplayName("ingestPlan() 正常流程测试")
  class NormalFlowTests {

    @Test
    @DisplayName("应成功创建新计划并发布事件")
    void shouldCreateNewPlanSuccessfully() {
      // Given: 准备测试数据
      PlanIngestionCommand command = createValidCommand();
      PlanAssemblyResult assemblyResult = createAssemblyResult();
      TaskQueuedEvent queuedEvent = createTaskQueuedEvent();
      PlanIngestionResult expectedResult = createIngestionResult();

      // Mock 依赖
      when(schedule.getId()).thenReturn(1L);
      when(plan.getPlanKey()).thenReturn("plan-key-001");
      when(plan.getId()).thenReturn(100L);
      when(plan.getProvenanceCode()).thenReturn(ProvenanceCode.PUBMED);
      when(plan.getOperationCode()).thenReturn(OperationCode.HARVEST.getCode());
      when(plan.getStatus()).thenReturn(PlanStatus.READY);

      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(ProvenanceCode.PUBMED, OperationCode.HARVEST))
          .thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), eq(configSnapshot), any(), any()))
          .thenReturn(window);
      when(planExpressionBuilder.build(any(), eq(configSnapshot))).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(0L);
      when(planAssembler.assemble(any(PlanAssemblyRequest.class))).thenReturn(assemblyResult);
      when(planRepository.findByPlanKey("plan-key-001")).thenReturn(Optional.empty());

      // Mock 持久化
      when(persistenceCoordinator.savePlan(plan)).thenReturn(plan);
      when(persistenceCoordinator.persistSlices(eq(plan), any())).thenReturn(List.of(slice));
      when(persistenceCoordinator.persistTasks(eq(plan), any(), any())).thenReturn(List.of(task));

      // Mock 发布
      when(publishingCoordinator.collectQueuedEvents(List.of(task)))
          .thenReturn(List.of(queuedEvent));
      when(publishingCoordinator.buildIngestionResult(
              eq(schedule), eq(plan), any(), eq(1), anyString()))
          .thenReturn(expectedResult);

      // When: 执行编排
      PlanIngestionResult result = handler.handle(command);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(expectedResult);

      // 验证调用顺序
      InOrder inOrder =
          inOrder(
              persistenceCoordinator,
              patraRegistryPort,
              cursorRepository,
              planningWindowResolver,
              planExpressionBuilder,
              taskRepository,
              plannerValidator,
              planAssembler,
              planRepository,
              publishingCoordinator);

      inOrder.verify(persistenceCoordinator).persistScheduleInstance(command);
      inOrder.verify(patraRegistryPort).fetchConfig(ProvenanceCode.PUBMED, OperationCode.HARVEST);
      inOrder
          .verify(cursorRepository)
          .findLatestGlobalTimeWatermark(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode());
      inOrder.verify(planningWindowResolver).resolveWindow(any(), eq(configSnapshot), any(), any());
      inOrder.verify(planExpressionBuilder).build(any(), eq(configSnapshot));
      inOrder
          .verify(taskRepository)
          .countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode());
      inOrder
          .verify(plannerValidator)
          .validateBeforeAssemble(any(), eq(configSnapshot), eq(window), eq(0L));
      inOrder.verify(planAssembler).assemble(any(PlanAssemblyRequest.class));
      inOrder.verify(planRepository).findByPlanKey("plan-key-001");
      inOrder.verify(persistenceCoordinator).savePlan(plan);
      inOrder.verify(persistenceCoordinator).persistSlices(eq(plan), any());
      inOrder.verify(persistenceCoordinator).persistTasks(eq(plan), any(), any());
      inOrder.verify(publishingCoordinator).collectQueuedEvents(List.of(task));
      inOrder
          .verify(publishingCoordinator)
          .publishNewPlanEvents(List.of(queuedEvent), plan, schedule);
      inOrder
          .verify(publishingCoordinator)
          .buildIngestionResult(eq(schedule), eq(plan), any(), eq(1), anyString());
    }

    @Test
    @DisplayName("应正确构建 PlanAssemblyRequest")
    void shouldBuildCorrectAssemblyRequest() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      PlanAssemblyResult assemblyResult = createAssemblyResult();

      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(ProvenanceCode.PUBMED, OperationCode.HARVEST))
          .thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), any(), any(), any())).thenReturn(window);
      when(planExpressionBuilder.build(any(), any())).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(0L);
      when(planAssembler.assemble(any(PlanAssemblyRequest.class))).thenReturn(assemblyResult);
      when(plan.getPlanKey()).thenReturn("plan-key-001");
      when(planRepository.findByPlanKey(anyString())).thenReturn(Optional.empty());

      setupPersistenceMocks();

      // When
      handler.handle(command);

      // Then: 验证 PlanAssemblyRequest 的构建
      verify(planAssembler).assemble(assemblyRequestCaptor.capture());
      PlanAssemblyRequest request = assemblyRequestCaptor.getValue();

      assertThat(request).isNotNull();
      assertThat(request.triggerNorm()).isNotNull();
      assertThat(request.window()).isEqualTo(window);
      assertThat(request.configSnapshot()).isEqualTo(configSnapshot);
      assertThat(request.planExpression()).isEqualTo(expressionDescriptor);
    }
  }

  @Nested
  @DisplayName("幂等性处理测试")
  class IdempotencyTests {

    @Test
    @DisplayName("当检测到重复计划时应复用现有计划")
    void shouldReuseExistingPlanWhenDuplicate() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      PlanAssemblyResult assemblyResult = createAssemblyResult();
      PlanAggregate existingPlan = plan;
      PlanIngestionResult expectedResult = createIngestionResult();

      when(schedule.getId()).thenReturn(1L);
      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(ProvenanceCode.PUBMED, OperationCode.HARVEST))
          .thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.of(Instant.parse("2025-01-01T00:00:00Z")));
      when(planningWindowResolver.resolveWindow(any(), any(), any(), any())).thenReturn(window);
      when(planExpressionBuilder.build(any(), any())).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(5L);
      when(planAssembler.assemble(any(PlanAssemblyRequest.class))).thenReturn(assemblyResult);
      when(plan.getPlanKey()).thenReturn("plan-key-001");
      when(planRepository.findByPlanKey("plan-key-001")).thenReturn(Optional.of(existingPlan));

      when(idempotencyCoordinator.handleIdempotentPlanReuse(existingPlan, schedule, "plan-key-001"))
          .thenReturn(expectedResult);

      // When
      PlanIngestionResult result = handler.handle(command);

      // Then
      assertThat(result).isEqualTo(expectedResult);
      verify(idempotencyCoordinator)
          .handleIdempotentPlanReuse(existingPlan, schedule, "plan-key-001");

      // 验证未执行持久化
      verify(persistenceCoordinator, never()).savePlan(any());
      verify(persistenceCoordinator, never()).persistSlices(any(), any());
      verify(persistenceCoordinator, never()).persistTasks(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("异常场景测试")
  class ExceptionTests {

    @Test
    @DisplayName("当游标水位查询失败时应抛出 PlanPersistenceException")
    void shouldThrowPlanPersistenceExceptionWhenCursorQueryFails() {
      // Given
      PlanIngestionCommand command = createValidCommand();

      when(schedule.getId()).thenReturn(1L);
      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(any(), any())).thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(any(ProvenanceCode.class), anyString()))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(PlanPersistenceException.class)
          .hasMessageContaining("加载游标水位失败");

      verify(planningWindowResolver, never()).resolveWindow(any(), any(), any(), any());
    }

    @Test
    @DisplayName("当窗口解析失败时应抛出 PlanValidationException")
    void shouldThrowPlanValidationExceptionWhenWindowResolutionFails() {
      // Given
      PlanIngestionCommand command = createValidCommand();

      when(schedule.getId()).thenReturn(1L);
      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(any(), any())).thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(any(ProvenanceCode.class), anyString()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), any(), any(), any()))
          .thenThrow(new RuntimeException("Invalid window configuration"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("解析计划窗口失败");
    }

    @Test
    @DisplayName("当预验证失败时应抛出 PlanValidationException")
    void shouldThrowPlanValidationExceptionWhenPreValidationFails() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      PlanValidationException validationException =
          new PlanValidationException("背压超过阈值", PlanValidationException.Reason.QUEUE_BACKPRESSURE);

      when(schedule.getId()).thenReturn(1L);
      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(any(), any())).thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), any(), any(), any())).thenReturn(window);
      when(planExpressionBuilder.build(any(), any())).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(1000L);
      doThrow(validationException)
          .when(plannerValidator)
          .validateBeforeAssemble(
              any(PlanTriggerNorm.class), any(), any(PlannerWindow.class), eq(1000L));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("背压超过阈值");

      verify(planAssembler, never()).assemble(any());
    }

    @Test
    @DisplayName("当组装返回 FAILED 状态时应抛出 PlanAssemblyException")
    void shouldThrowPlanAssemblyExceptionWhenAssemblyFails() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      // 使用 Mock 而不是 null
      PlanAggregate mockPlan = org.mockito.Mockito.mock(PlanAggregate.class);
      PlanAssemblyResult failedResult =
          new PlanAssemblyResult(
              mockPlan,
              Collections.emptyList(),
              Collections.emptyList(),
              PlanAssemblyResult.AssemblyStatus.FAILED);

      when(schedule.getId()).thenReturn(1L);
      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(any(), any())).thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), any(), any(), any())).thenReturn(window);
      when(planExpressionBuilder.build(any(), any())).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(0L);
      when(planAssembler.assemble(any())).thenReturn(failedResult);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(PlanAssemblyException.class)
          .hasMessageContaining("计划组装未生成可执行单元");
    }

    @Test
    @DisplayName("当组装返回 null 时应抛出 PlanAssemblyException")
    void shouldThrowPlanAssemblyExceptionWhenAssemblyReturnsNull() {
      // Given
      PlanIngestionCommand command = createValidCommand();

      when(schedule.getId()).thenReturn(1L);
      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(any(), any())).thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), any(), any(), any())).thenReturn(window);
      when(planExpressionBuilder.build(any(), any())).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(0L);
      when(planAssembler.assemble(any())).thenReturn(null);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(PlanAssemblyException.class)
          .hasMessageContaining("计划组装未生成可执行单元");
    }

    @Test
    @DisplayName("当持久化失败时应传播 PlanPersistenceException")
    void shouldPropagatePlanPersistenceExceptionWhenPersistenceFails() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      PlanAssemblyResult assemblyResult = createAssemblyResult();
      PlanPersistenceException persistenceException =
          new PlanPersistenceException(PlanPersistenceException.Stage.PLAN, "持久化计划失败", null);

      when(schedule.getId()).thenReturn(1L);
      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(any(), any())).thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), any(), any(), any())).thenReturn(window);
      when(planExpressionBuilder.build(any(), any())).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(0L);
      when(planAssembler.assemble(any())).thenReturn(assemblyResult);
      when(plan.getPlanKey()).thenReturn("plan-key-001");
      when(planRepository.findByPlanKey(anyString())).thenReturn(Optional.empty());
      when(plan.getId()).thenReturn(100L);
      when(plan.getProvenanceCode()).thenReturn(ProvenanceCode.PUBMED);
      when(plan.getOperationCode()).thenReturn(OperationCode.HARVEST.getCode());
      when(persistenceCoordinator.savePlan(plan)).thenThrow(persistenceException);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(PlanPersistenceException.class)
          .hasMessageContaining("持久化计划失败");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryTests {

    @Test
    @DisplayName("当游标水位为 null 时应正常处理（首次运行）")
    void shouldHandleNullCursorWatermark() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      PlanAssemblyResult assemblyResult = createAssemblyResult();

      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(any(), any())).thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), eq(configSnapshot), eq(null), any()))
          .thenReturn(window);
      when(planExpressionBuilder.build(any(), any())).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(0L);
      when(planAssembler.assemble(any())).thenReturn(assemblyResult);
      when(plan.getPlanKey()).thenReturn("plan-key-001");
      when(planRepository.findByPlanKey(anyString())).thenReturn(Optional.empty());

      setupPersistenceMocks();

      // When
      PlanIngestionResult result = handler.handle(command);

      // Then
      assertThat(result).isNotNull();
      verify(planningWindowResolver).resolveWindow(any(), eq(configSnapshot), eq(null), any());
    }

    @Test
    @DisplayName("当队列任务数为 0 时应正常处理")
    void shouldHandleZeroQueuedTasks() {
      // Given
      PlanIngestionCommand command = createValidCommand();
      PlanAssemblyResult assemblyResult = createAssemblyResult();

      when(persistenceCoordinator.persistScheduleInstance(command)).thenReturn(schedule);
      when(patraRegistryPort.fetchConfig(any(), any())).thenReturn(configSnapshot);
      when(cursorRepository.findLatestGlobalTimeWatermark(
              ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(Optional.empty());
      when(planningWindowResolver.resolveWindow(any(), any(), any(), any())).thenReturn(window);
      when(planExpressionBuilder.build(any(), any())).thenReturn(expressionDescriptor);
      when(expressionDescriptor.hash()).thenReturn("hash-001");
      when(expressionDescriptor.jsonSnapshot()).thenReturn("{}");
      when(taskRepository.countQueuedTasks(ProvenanceCode.PUBMED, OperationCode.HARVEST.getCode()))
          .thenReturn(0L);
      when(planAssembler.assemble(any())).thenReturn(assemblyResult);
      when(plan.getPlanKey()).thenReturn("plan-key-001");
      when(planRepository.findByPlanKey(anyString())).thenReturn(Optional.empty());

      setupPersistenceMocks();

      // When
      handler.handle(command);

      // Then
      verify(plannerValidator).validateBeforeAssemble(any(), any(), any(), eq(0L));
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

  /// 创建 PlanAssemblyResult。
  private PlanAssemblyResult createAssemblyResult() {
    return new PlanAssemblyResult(
        plan, List.of(slice), List.of(task), PlanAssemblyResult.AssemblyStatus.READY);
  }

  /// 创建 TaskQueuedEvent。
  private TaskQueuedEvent createTaskQueuedEvent() {
    return TaskQueuedEvent.of(
        1L,
        100L,
        10L,
        1L,
        ProvenanceCode.PUBMED,
        OperationCode.HARVEST.getCode(),
        "task-key-001",
        "{}",
        1,
        Instant.now());
  }

  /// 创建 PlanIngestionResult。
  private PlanIngestionResult createIngestionResult() {
    return new PlanIngestionResult(1L, 100L, List.of(10L), 1, "SUCCESS");
  }

  /// 设置持久化相关的 Mocks。
  private void setupPersistenceMocks() {
    when(schedule.getId()).thenReturn(1L);
    when(plan.getId()).thenReturn(100L);
    when(plan.getProvenanceCode()).thenReturn(ProvenanceCode.PUBMED);
    when(plan.getOperationCode()).thenReturn(OperationCode.HARVEST.getCode());
    when(plan.getStatus()).thenReturn(PlanStatus.READY);
    when(slice.getId()).thenReturn(10L);
    when(task.getId()).thenReturn(1L);

    when(persistenceCoordinator.savePlan(plan)).thenReturn(plan);
    when(persistenceCoordinator.persistSlices(eq(plan), any())).thenReturn(List.of(slice));
    when(persistenceCoordinator.persistTasks(eq(plan), any(), any())).thenReturn(List.of(task));
    when(publishingCoordinator.collectQueuedEvents(any())).thenReturn(Collections.emptyList());
    when(publishingCoordinator.buildIngestionResult(
            any(), any(), any(), any(Integer.class), anyString()))
        .thenReturn(createIngestionResult());
  }
}
