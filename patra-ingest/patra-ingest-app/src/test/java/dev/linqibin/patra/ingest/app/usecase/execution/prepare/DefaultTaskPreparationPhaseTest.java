package dev.linqibin.patra.ingest.app.usecase.execution.prepare;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import dev.linqibin.patra.ingest.app.usecase.execution.idempotency.IdempotencyChecker;
import dev.linqibin.patra.ingest.app.usecase.execution.lease.LeaseManagementService;
import dev.linqibin.patra.ingest.app.usecase.execution.session.ExecutionContextLoader;
import dev.linqibin.patra.ingest.app.usecase.execution.session.ExecutionSession;
import dev.linqibin.patra.ingest.app.usecase.execution.session.ExecutionSessionManager;
import dev.linqibin.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import dev.linqibin.patra.ingest.domain.model.aggregate.TaskAggregate;
import dev.linqibin.patra.ingest.domain.model.entity.TaskRun;
import dev.linqibin.patra.ingest.domain.model.enums.SliceStatus;
import dev.linqibin.patra.ingest.domain.model.enums.TaskStatus;
import dev.linqibin.patra.ingest.domain.model.vo.execution.ExecutionContext;
import dev.linqibin.patra.ingest.domain.model.vo.slice.PlanSliceId;
import dev.linqibin.patra.ingest.domain.model.vo.task.TaskId;
import dev.linqibin.patra.ingest.domain.port.PlanSliceRepository;
import dev.linqibin.patra.ingest.domain.port.TaskRepository;
import dev.linqibin.patra.ingest.domain.port.TaskRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/// DefaultTaskPreparationPhase 单元测试
///
/// 测试范围:
///
/// - ✅ 正常流程: 幂等检查 → 租约获取 → 会话创建 → 上下文加载
///   - ✅ 幂等跳过: 任务已成功时抛出异常
///   - ✅ 租约失败: 租约获取失败时抛出异常
///   - ✅ 异常处理: 失败时资源清理（心跳停止、租约释放）
///   - ✅ Slice 状态转换: PENDING → ASSIGNED
///   - ✅ Task/TaskRun 状态更新: 标记为 RUNNING
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DefaultTaskPreparationPhase 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultTaskPreparationPhaseTest {

  @Mock private TaskRepository taskRepository;
  @Mock private PlanSliceRepository planSliceRepository;
  @Mock private IdempotencyChecker idempotencyChecker;
  @Mock private LeaseManagementService leaseManagementService;
  @Mock private ExecutionSessionManager sessionManager;
  @Mock private ExecutionContextLoader contextLoader;
  @Mock private TaskRunRepository taskRunRepository;
  @Mock private Clock clock;

  @InjectMocks private DefaultTaskPreparationPhase preparePhase;

  private TaskReadyCommand command;
  private TaskAggregate mockTask;
  private PlanSliceAggregate mockSlice;
  private TaskRun mockTaskRun;
  private ExecutionSession mockSession;
  private ExecutionContext mockContext;
  private Instant fixedNow;

  @BeforeEach
  void setUp() {
    fixedNow = Instant.parse("2025-01-06T10:00:00Z");
    when(clock.instant()).thenReturn(fixedNow);
    when(clock.getZone()).thenReturn(ZoneId.systemDefault());

    // 设置租约持续时间配置
    ReflectionTestUtils.setField(preparePhase, "leaseDurationSeconds", 60);

    command = createTestCommand();
    mockTask = createMockTask();
    mockSlice = createMockSlice();
    mockTaskRun = createMockTaskRun();
    mockSession = createMockSession();
    mockContext = createMockContext();
  }

  // ========== 正常流程测试 ==========

  @Nested
  @DisplayName("正常流程")
  class HappyPathTests {

    @Test
    @DisplayName("应该成功执行完整的准备流程")
    void shouldExecuteCompletePrepareFlow() {
      // Given: Mock 所有依赖
      when(idempotencyChecker.isAlreadySucceeded(command.taskId(), command.idempotentKey()))
          .thenReturn(false);
      when(leaseManagementService.tryAcquireLease(
              eq(command.taskId()), anyString(), any(Duration.class)))
          .thenReturn(true);
      when(taskRepository.findById(command.taskId())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(mockTask.getSliceId().value()))
          .thenReturn(Optional.of(mockSlice));
      when(sessionManager.createSession(eq(mockTask), anyString(), eq(command.getCorrelationId())))
          .thenReturn(mockSession);
      when(contextLoader.loadContext(any(TaskAggregate.class), anyLong())).thenReturn(mockContext);
      when(taskRunRepository.findById(mockSession.runId())).thenReturn(Optional.of(mockTaskRun));

      // When: 执行准备
      TaskPreparationPhase.PrepareResult result = preparePhase.prepare(command);

      // Then: 验证返回结果
      assertThat(result).isNotNull();
      assertThat(result.session()).isEqualTo(mockSession);
      assertThat(result.context()).isEqualTo(mockContext);

      // 验证调用顺序
      InOrder inOrder =
          inOrder(
              idempotencyChecker,
              leaseManagementService,
              taskRepository,
              planSliceRepository,
              sessionManager,
              contextLoader,
              taskRunRepository);
      inOrder
          .verify(idempotencyChecker)
          .isAlreadySucceeded(command.taskId(), command.idempotentKey());
      inOrder
          .verify(leaseManagementService)
          .tryAcquireLease(eq(command.taskId()), anyString(), any(Duration.class));
      inOrder.verify(taskRepository).findById(command.taskId());
      inOrder.verify(planSliceRepository).findById(mockTask.getSliceId().value());
      inOrder
          .verify(sessionManager)
          .createSession(eq(mockTask), anyString(), eq(command.getCorrelationId()));
      inOrder.verify(contextLoader).loadContext(mockTask, mockSession.runId());
      inOrder.verify(taskRunRepository).findById(mockSession.runId());
    }

    @Test
    @DisplayName("应该标记 PENDING 状态的 Slice 为 ASSIGNED")
    void shouldMarkPendingSliceAsAssigned() {
      // Given: Slice 状态为 PENDING
      when(mockSlice.getStatus()).thenReturn(SliceStatus.PENDING);
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(anyLong())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(anyLong())).thenReturn(Optional.of(mockSlice));
      when(sessionManager.createSession(any(TaskAggregate.class), anyString(), anyString()))
          .thenReturn(mockSession);
      when(contextLoader.loadContext(any(TaskAggregate.class), anyLong())).thenReturn(mockContext);
      when(taskRunRepository.findById(anyLong())).thenReturn(Optional.of(mockTaskRun));

      // When: 执行准备
      preparePhase.prepare(command);

      // Then: Slice 应该被标记为 ASSIGNED 并保存
      verify(mockSlice).markAssigned();
      verify(planSliceRepository).save(mockSlice);
    }

    @Test
    @DisplayName("当 Slice 已经是 ASSIGNED 状态时不应该重复标记")
    void shouldNotRemarkSliceWhenAlreadyAssigned() {
      // Given: Slice 已经是 ASSIGNED 状态
      when(mockSlice.getStatus()).thenReturn(SliceStatus.ASSIGNED);
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(anyLong())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(anyLong())).thenReturn(Optional.of(mockSlice));
      when(sessionManager.createSession(any(TaskAggregate.class), anyString(), anyString()))
          .thenReturn(mockSession);
      when(contextLoader.loadContext(any(TaskAggregate.class), anyLong())).thenReturn(mockContext);
      when(taskRunRepository.findById(anyLong())).thenReturn(Optional.of(mockTaskRun));

      // When: 执行准备
      preparePhase.prepare(command);

      // Then: 不应该调用 markAssigned 和 save
      verify(mockSlice, never()).markAssigned();
      verify(planSliceRepository, never()).save(mockSlice);
    }

    @Test
    @DisplayName("应该更新 TaskRun 和 Task 状态为 RUNNING")
    void shouldUpdateTaskRunAndTaskToRunning() {
      // Given: Mock 所有依赖
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(anyLong())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(anyLong())).thenReturn(Optional.of(mockSlice));
      when(sessionManager.createSession(any(TaskAggregate.class), anyString(), anyString()))
          .thenReturn(mockSession);
      when(contextLoader.loadContext(any(TaskAggregate.class), anyLong())).thenReturn(mockContext);
      when(taskRunRepository.findById(anyLong())).thenReturn(Optional.of(mockTaskRun));

      // When: 执行准备
      preparePhase.prepare(command);

      // Then: TaskRun 和 Task 应该被标记为 RUNNING
      verify(mockTaskRun).bindRunContext(command.getCorrelationId());
      verify(mockTaskRun).start(fixedNow);
      verify(taskRunRepository).save(mockTaskRun);
      verify(mockTask).markRunning(fixedNow, command.getCorrelationId());
      verify(taskRepository).save(mockTask);
    }
  }

  // ========== 幂等跳过场景 ==========

  @Nested
  @DisplayName("幂等跳过场景")
  class IdempotentSkipTests {

    @Test
    @DisplayName("当任务已成功时应该抛出 TaskAlreadySucceededException")
    void shouldThrowTaskAlreadySucceededExceptionWhenTaskAlreadySucceeded() {
      // Given: 幂等检查返回 true
      when(idempotencyChecker.isAlreadySucceeded(command.taskId(), command.idempotentKey()))
          .thenReturn(true);

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> preparePhase.prepare(command))
          .isInstanceOf(TaskPreparationPhase.TaskAlreadySucceededException.class)
          .hasMessageContaining("任务已成功")
          .hasMessageContaining("taskId=" + command.taskId());
    }

    @Test
    @DisplayName("幂等跳过时不应该获取租约")
    void shouldNotAcquireLeaseWhenIdempotentSkip() {
      // Given: 幂等检查返回 true
      when(idempotencyChecker.isAlreadySucceeded(command.taskId(), command.idempotentKey()))
          .thenReturn(true);

      // When: 尝试准备（会抛出异常）
      try {
        preparePhase.prepare(command);
      } catch (TaskPreparationPhase.TaskAlreadySucceededException e) {
        // 忽略异常
      }

      // Then: 不应该调用租约管理
      verifyNoInteractions(leaseManagementService);
    }
  }

  // ========== 租约失败场景 ==========

  @Nested
  @DisplayName("租约失败场景")
  class LeaseFailureTests {

    @Test
    @DisplayName("当租约获取失败时应该抛出 LeaseAcquisitionFailedException")
    void shouldThrowLeaseAcquisitionFailedExceptionWhenLeaseAcquisitionFails() {
      // Given: 幂等检查通过，租约获取失败
      when(idempotencyChecker.isAlreadySucceeded(command.taskId(), command.idempotentKey()))
          .thenReturn(false);
      when(leaseManagementService.tryAcquireLease(
              eq(command.taskId()), anyString(), any(Duration.class)))
          .thenReturn(false);

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> preparePhase.prepare(command))
          .isInstanceOf(TaskPreparationPhase.LeaseAcquisitionFailedException.class)
          .hasMessageContaining("租约获取失败")
          .hasMessageContaining("taskId=" + command.taskId());
    }

    @Test
    @DisplayName("租约失败时不应该创建会话")
    void shouldNotCreateSessionWhenLeaseFails() {
      // Given: 租约获取失败
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(false);

      // When: 尝试准备（会抛出异常）
      try {
        preparePhase.prepare(command);
      } catch (TaskPreparationPhase.LeaseAcquisitionFailedException e) {
        // 忽略异常
      }

      // Then: 不应该调用会话管理器
      verifyNoInteractions(sessionManager);
    }

    @Test
    @DisplayName("应该使用配置的租约持续时间")
    void shouldUseConfiguredLeaseDuration() {
      // Given: 设置租约持续时间为 120 秒
      ReflectionTestUtils.setField(preparePhase, "leaseDurationSeconds", 120);
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(false);

      // When: 尝试准备（会抛出异常）
      try {
        preparePhase.prepare(command);
      } catch (TaskPreparationPhase.LeaseAcquisitionFailedException e) {
        // 忽略异常
      }

      // Then: 应该使用 120 秒的租约持续时间
      verify(leaseManagementService)
          .tryAcquireLease(eq(command.taskId()), anyString(), eq(Duration.ofSeconds(120)));
    }
  }

  // ========== 异常处理测试 ==========

  @Nested
  @DisplayName("异常处理")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("当会话创建失败时应该停止心跳并释放租约")
    void shouldStopHeartbeatAndReleaseLeaseWhenSessionCreationFails() {
      // Given: 会话创建失败
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(anyLong())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(anyLong())).thenReturn(Optional.of(mockSlice));
      when(sessionManager.createSession(any(TaskAggregate.class), anyString(), anyString()))
          .thenThrow(new RuntimeException("会话创建失败"));

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> preparePhase.prepare(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("会话创建失败");

      // 验证资源清理不被调用（因为会话未创建）
      verifyNoInteractions(mockSession);
    }

    @Test
    @DisplayName("当上下文加载失败时应该停止心跳并释放租约")
    void shouldStopHeartbeatAndReleaseLeaseWhenContextLoadingFails() {
      // Given: 上下文加载失败
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(anyLong())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(anyLong())).thenReturn(Optional.of(mockSlice));
      when(sessionManager.createSession(any(TaskAggregate.class), anyString(), anyString()))
          .thenReturn(mockSession);
      when(contextLoader.loadContext(any(TaskAggregate.class), anyLong()))
          .thenThrow(new RuntimeException("上下文加载失败"));

      ExecutionSession.HeartbeatHandle heartbeatHandle =
          mock(ExecutionSession.HeartbeatHandle.class);
      when(mockSession.heartbeatHandle()).thenReturn(heartbeatHandle);

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> preparePhase.prepare(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("上下文加载失败");

      // 验证资源清理
      verify(heartbeatHandle).stop();
      verify(leaseManagementService).releaseLease(command.taskId());
    }

    @Test
    @DisplayName("当 TaskRun 更新失败时应该停止心跳并释放租约")
    void shouldStopHeartbeatAndReleaseLeaseWhenTaskRunUpdateFails() {
      // Given: TaskRun 未找到
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(anyLong())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(anyLong())).thenReturn(Optional.of(mockSlice));
      when(sessionManager.createSession(any(TaskAggregate.class), anyString(), anyString()))
          .thenReturn(mockSession);
      when(contextLoader.loadContext(any(TaskAggregate.class), anyLong())).thenReturn(mockContext);
      when(taskRunRepository.findById(mockSession.runId())).thenReturn(Optional.empty());

      ExecutionSession.HeartbeatHandle heartbeatHandle =
          mock(ExecutionSession.HeartbeatHandle.class);
      when(mockSession.heartbeatHandle()).thenReturn(heartbeatHandle);

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> preparePhase.prepare(command))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("未找到 TaskRun");

      // 验证资源清理
      verify(heartbeatHandle).stop();
      verify(leaseManagementService).releaseLease(command.taskId());
    }

    @Test
    @DisplayName("资源清理失败时不应该影响原始异常传播")
    void shouldNotSuppressOriginalExceptionWhenCleanupFails() {
      // Given: 上下文加载失败，且资源清理也失败
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(anyLong())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(anyLong())).thenReturn(Optional.of(mockSlice));
      when(sessionManager.createSession(any(TaskAggregate.class), anyString(), anyString()))
          .thenReturn(mockSession);
      when(contextLoader.loadContext(any(TaskAggregate.class), anyLong()))
          .thenThrow(new RuntimeException("上下文加载失败"));

      ExecutionSession.HeartbeatHandle heartbeatHandle =
          mock(ExecutionSession.HeartbeatHandle.class);
      when(mockSession.heartbeatHandle()).thenReturn(heartbeatHandle);
      doThrow(new RuntimeException("心跳停止失败")).when(heartbeatHandle).stop();

      // When & Then: 应该抛出原始异常
      assertThatThrownBy(() -> preparePhase.prepare(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("上下文加载失败");
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件")
  class EdgeCaseTests {

    @Test
    @DisplayName("当 Task 未找到时应该抛出异常")
    void shouldThrowExceptionWhenTaskNotFound() {
      // Given: Task 不存在
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(command.taskId())).thenReturn(Optional.empty());

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> preparePhase.prepare(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未找到任务")
          .hasMessageContaining("taskId=" + command.taskId());
    }

    @Test
    @DisplayName("当 Slice 未找到时应该抛出异常")
    void shouldThrowExceptionWhenSliceNotFound() {
      // Given: Slice 不存在
      when(idempotencyChecker.isAlreadySucceeded(anyLong(), anyString())).thenReturn(false);
      when(leaseManagementService.tryAcquireLease(anyLong(), anyString(), any())).thenReturn(true);
      when(taskRepository.findById(anyLong())).thenReturn(Optional.of(mockTask));
      when(planSliceRepository.findById(mockTask.getSliceId().value()))
          .thenReturn(Optional.empty());

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> preparePhase.prepare(command))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("未找到切片")
          .hasMessageContaining("sliceId=" + mockTask.getSliceId());
    }
  }

  // ========== 辅助方法 ==========

  private TaskReadyCommand createTestCommand() {
    String correlationId = UUID.randomUUID().toString();
    return new TaskReadyCommand(
        1001L, // taskId
        "idempotent-key-001", // idempotentKey
        java.util.Map.of("correlationId", correlationId) // headers
        );
  }

  private TaskAggregate createMockTask() {
    TaskAggregate task = mock(TaskAggregate.class);
    when(task.getId()).thenReturn(TaskId.of(1001L));
    when(task.getSliceId()).thenReturn(PlanSliceId.of(3001L));
    when(task.getStatus()).thenReturn(TaskStatus.QUEUED);
    doNothing().when(task).markRunning(any(Instant.class), anyString());
    return task;
  }

  private PlanSliceAggregate createMockSlice() {
    PlanSliceAggregate slice = mock(PlanSliceAggregate.class);
    when(slice.getId()).thenReturn(PlanSliceId.of(3001L));
    when(slice.getStatus()).thenReturn(SliceStatus.PENDING);
    doNothing().when(slice).markAssigned();
    return slice;
  }

  private TaskRun createMockTaskRun() {
    TaskRun taskRun = mock(TaskRun.class);
    doNothing().when(taskRun).bindRunContext(anyString());
    doNothing().when(taskRun).start(any(Instant.class));
    return taskRun;
  }

  private ExecutionSession createMockSession() {
    ExecutionSession session = mock(ExecutionSession.class);
    ExecutionSession.HeartbeatHandle heartbeatHandle = mock(ExecutionSession.HeartbeatHandle.class);

    when(session.taskId()).thenReturn(1001L);
    when(session.runId()).thenReturn(2001L);
    when(session.leaseOwner()).thenReturn("worker-1");
    when(session.heartbeatHandle()).thenReturn(heartbeatHandle);

    return session;
  }

  private ExecutionContext createMockContext() {
    ExecutionContext context = mock(ExecutionContext.class);
    when(context.provenanceCode()).thenReturn(ProvenanceCode.PUBMED);
    when(context.operationCode()).thenReturn("harvest");
    return context;
  }
}
