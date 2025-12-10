package com.patra.ingest.app.usecase.execution;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.complete.TaskCompletionPhase;
import com.patra.ingest.app.usecase.execution.prepare.TaskPreparationPhase;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.BatchExecutionPhase;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
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

/// TaskExecutionHandler 单元测试
///
/// 测试范围:
///
/// - ✅ 正常流程: 准备 → 执行 → 完成
/// - ✅ 幂等跳过: 任务已成功场景
/// - ✅ 租约失败: 租约获取失败场景
/// - ✅ 异常处理: 各阶段异常的资源清理
/// - ✅ 调用顺序验证: 三阶段编排顺序
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("TaskExecutionHandler 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskExecutionHandlerTest {

  @Mock private TaskPreparationPhase preparePhase;
  @Mock private BatchExecutionPhase executePhase;
  @Mock private TaskCompletionPhase completePhase;

  @InjectMocks private TaskExecutionHandler taskExecutionHandler;

  private TaskReadyCommand command;
  private ExecutionSession mockSession;
  private ExecutionContext mockContext;

  @BeforeEach
  void setUp() {
    command = createTestCommand();
    mockSession = createMockSession();
    mockContext = createMockContext();
  }

  // ========== 正常流程测试 ==========

  @Nested
  @DisplayName("正常流程")
  class HappyPathTests {

    @Test
    @DisplayName("应该成功执行完整的三阶段流程")
    void shouldExecuteCompleteThreePhaseFlow() {
      // Given: Mock 准备阶段返回
      TaskPreparationPhase.PrepareResult prepareResult =
          new TaskPreparationPhase.PrepareResult(mockSession, mockContext);
      when(preparePhase.prepare(command)).thenReturn(prepareResult);

      // Mock 执行阶段返回
      BatchExecutionPhase.ExecuteResult executeResult =
          new BatchExecutionPhase.ExecuteResult(10, 10, 0);
      when(executePhase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionHandler.handle(command);

      // Then: 验证三个阶段都被调用
      InOrder inOrder = inOrder(preparePhase, executePhase, completePhase);
      inOrder.verify(preparePhase).prepare(command);
      inOrder.verify(executePhase).execute(mockSession, mockContext);
      inOrder.verify(completePhase).complete(mockSession, mockContext, executeResult);
    }

    @Test
    @DisplayName("应该正确传递执行结果给完成阶段")
    void shouldPassExecuteResultToCompletePhase() {
      // Given: Mock 准备和执行阶段
      TaskPreparationPhase.PrepareResult prepareResult =
          new TaskPreparationPhase.PrepareResult(mockSession, mockContext);
      when(preparePhase.prepare(command)).thenReturn(prepareResult);

      BatchExecutionPhase.ExecuteResult executeResult =
          new BatchExecutionPhase.ExecuteResult(20, 18, 2);
      when(executePhase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionHandler.handle(command);

      // Then: 完成阶段应该接收正确的执行结果
      verify(completePhase).complete(mockSession, mockContext, executeResult);
    }
  }

  // ========== 幂等跳过场景 ==========

  @Nested
  @DisplayName("幂等跳过场景")
  class IdempotentSkipTests {

    @Test
    @DisplayName("当任务已成功时应该跳过执行并返回")
    void shouldSkipExecutionWhenTaskAlreadySucceeded() {
      // Given: 准备阶段抛出任务已成功异常
      when(preparePhase.prepare(command))
          .thenThrow(
              new TaskPreparationPhase.TaskAlreadySucceededException(
                  "任务已成功 taskId=" + command.taskId()));

      // When: 执行任务（不应该抛出异常）
      taskExecutionHandler.handle(command);

      // Then: 执行和完成阶段不应该被调用
      verify(preparePhase).prepare(command);
      verifyNoInteractions(executePhase);
      verifyNoInteractions(completePhase);
    }

    @Test
    @DisplayName("幂等跳过时不应该抛出异常")
    void shouldNotThrowExceptionWhenIdempotentSkip() {
      // Given: 准备阶段抛出任务已成功异常
      when(preparePhase.prepare(command))
          .thenThrow(new TaskPreparationPhase.TaskAlreadySucceededException("任务已成功"));

      // When & Then: 执行任务不应该抛出异常
      assertThatCode(() -> taskExecutionHandler.handle(command)).doesNotThrowAnyException();
    }
  }

  // ========== 租约失败场景 ==========

  @Nested
  @DisplayName("租约失败场景")
  class LeaseFailureTests {

    @Test
    @DisplayName("当租约获取失败时应该跳过执行并返回")
    void shouldSkipExecutionWhenLeaseAcquisitionFails() {
      // Given: 准备阶段抛出租约获取失败异常
      when(preparePhase.prepare(command))
          .thenThrow(
              new TaskPreparationPhase.LeaseAcquisitionFailedException(
                  "租约获取失败 taskId=" + command.taskId()));

      // When: 执行任务（不应该抛出异常）
      taskExecutionHandler.handle(command);

      // Then: 执行和完成阶段不应该被调用
      verify(preparePhase).prepare(command);
      verifyNoInteractions(executePhase);
      verifyNoInteractions(completePhase);
    }

    @Test
    @DisplayName("租约失败时不应该抛出异常")
    void shouldNotThrowExceptionWhenLeaseAcquisitionFails() {
      // Given: 准备阶段抛出租约获取失败异常
      when(preparePhase.prepare(command))
          .thenThrow(new TaskPreparationPhase.LeaseAcquisitionFailedException("租约失败"));

      // When & Then: 执行任务不应该抛出异常
      assertThatCode(() -> taskExecutionHandler.handle(command)).doesNotThrowAnyException();
    }
  }

  // ========== 异常处理测试 ==========

  @Nested
  @DisplayName("异常处理")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("当执行阶段失败时应该清理会话资源")
    void shouldCleanupSessionWhenExecutePhaseFails() {
      // Given: 准备阶段成功，执行阶段失败
      TaskPreparationPhase.PrepareResult prepareResult =
          new TaskPreparationPhase.PrepareResult(mockSession, mockContext);
      when(preparePhase.prepare(command)).thenReturn(prepareResult);
      when(executePhase.execute(mockSession, mockContext)).thenThrow(new RuntimeException("执行失败"));

      // When & Then: 执行任务应该抛出 TaskExecutionException
      assertThatThrownBy(() -> taskExecutionHandler.handle(command))
          .isInstanceOf(TaskExecutionHandler.TaskExecutionException.class)
          .hasCauseInstanceOf(RuntimeException.class)
          .hasMessageContaining("任务执行失败");

      // 验证会话清理被调用
      verify(mockSession).cleanup();
    }

    @Test
    @DisplayName("当完成阶段失败时应该清理会话资源")
    void shouldCleanupSessionWhenCompletePhaseFails() {
      // Given: 准备和执行阶段成功，完成阶段失败
      TaskPreparationPhase.PrepareResult prepareResult =
          new TaskPreparationPhase.PrepareResult(mockSession, mockContext);
      when(preparePhase.prepare(command)).thenReturn(prepareResult);

      BatchExecutionPhase.ExecuteResult executeResult =
          new BatchExecutionPhase.ExecuteResult(10, 10, 0);
      when(executePhase.execute(mockSession, mockContext)).thenReturn(executeResult);

      doThrow(new RuntimeException("完成失败"))
          .when(completePhase)
          .complete(mockSession, mockContext, executeResult);

      // When & Then: 执行任务应该抛出 TaskExecutionException
      assertThatThrownBy(() -> taskExecutionHandler.handle(command))
          .isInstanceOf(TaskExecutionHandler.TaskExecutionException.class)
          .hasCauseInstanceOf(RuntimeException.class);

      // 验证会话清理被调用
      verify(mockSession).cleanup();
    }

    @Test
    @DisplayName("当会话清理失败时不应该影响异常传播")
    void shouldNotSuppressExceptionWhenSessionCleanupFails() {
      // Given: 执行失败且会话清理也失败
      TaskPreparationPhase.PrepareResult prepareResult =
          new TaskPreparationPhase.PrepareResult(mockSession, mockContext);
      when(preparePhase.prepare(command)).thenReturn(prepareResult);
      when(executePhase.execute(mockSession, mockContext)).thenThrow(new RuntimeException("执行失败"));
      doThrow(new RuntimeException("清理失败")).when(mockSession).cleanup();

      // When & Then: 仍然应该抛出原始的执行异常
      assertThatThrownBy(() -> taskExecutionHandler.handle(command))
          .isInstanceOf(TaskExecutionHandler.TaskExecutionException.class)
          .hasMessageContaining("任务执行失败")
          .hasCauseInstanceOf(RuntimeException.class)
          .cause()
          .hasMessageContaining("执行失败");
    }

    @Test
    @DisplayName("准备阶段异常时不应该调用会话清理")
    void shouldNotCleanupSessionWhenPreparePhaseFailsWithGeneralException() {
      // Given: 准备阶段抛出非预期异常（非 TaskAlreadySucceededException 或
      // LeaseAcquisitionFailedException）
      when(preparePhase.prepare(command)).thenThrow(new RuntimeException("准备失败"));

      // When & Then: 执行任务应该抛出异常
      assertThatThrownBy(() -> taskExecutionHandler.handle(command))
          .isInstanceOf(TaskExecutionHandler.TaskExecutionException.class);

      // 验证会话清理不被调用（因为 session 为 null）
      verifyNoInteractions(mockSession);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件")
  class EdgeCaseTests {

    @Test
    @DisplayName("应该支持零批次的执行结果")
    void shouldSupportZeroBatchesExecuteResult() {
      // Given: 执行阶段返回零批次
      TaskPreparationPhase.PrepareResult prepareResult =
          new TaskPreparationPhase.PrepareResult(mockSession, mockContext);
      when(preparePhase.prepare(command)).thenReturn(prepareResult);

      BatchExecutionPhase.ExecuteResult executeResult =
          new BatchExecutionPhase.ExecuteResult(0, 0, 0);
      when(executePhase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionHandler.handle(command);

      // Then: 完成阶段仍应该被调用
      verify(completePhase).complete(mockSession, mockContext, executeResult);
    }

    @Test
    @DisplayName("应该支持部分失败的执行结果")
    void shouldSupportPartialFailureExecuteResult() {
      // Given: 执行阶段返回部分失败
      TaskPreparationPhase.PrepareResult prepareResult =
          new TaskPreparationPhase.PrepareResult(mockSession, mockContext);
      when(preparePhase.prepare(command)).thenReturn(prepareResult);

      BatchExecutionPhase.ExecuteResult executeResult =
          new BatchExecutionPhase.ExecuteResult(10, 7, 3);
      when(executePhase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionHandler.handle(command);

      // Then: 完成阶段应该接收部分失败的结果
      verify(completePhase).complete(mockSession, mockContext, executeResult);
    }

    @Test
    @DisplayName("应该支持全部失败的执行结果")
    void shouldSupportCompleteFailureExecuteResult() {
      // Given: 执行阶段返回全部失败
      TaskPreparationPhase.PrepareResult prepareResult =
          new TaskPreparationPhase.PrepareResult(mockSession, mockContext);
      when(preparePhase.prepare(command)).thenReturn(prepareResult);

      BatchExecutionPhase.ExecuteResult executeResult =
          new BatchExecutionPhase.ExecuteResult(5, 0, 5);
      when(executePhase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionHandler.handle(command);

      // Then: 完成阶段应该接收全部失败的结果
      verify(completePhase).complete(mockSession, mockContext, executeResult);
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

  private ExecutionSession createMockSession() {
    ExecutionSession session = mock(ExecutionSession.class);
    ExecutionSession.HeartbeatHandle heartbeatHandle = mock(ExecutionSession.HeartbeatHandle.class);

    when(session.taskId()).thenReturn(1001L);
    when(session.runId()).thenReturn(2001L);
    when(session.leaseOwner()).thenReturn("worker-1");
    when(session.heartbeatHandle()).thenReturn(heartbeatHandle);
    doNothing().when(session).cleanup();

    return session;
  }

  private ExecutionContext createMockContext() {
    ExecutionContext context = mock(ExecutionContext.class);
    when(context.provenanceCode()).thenReturn(ProvenanceCode.PUBMED);
    when(context.operationCode()).thenReturn("harvest");
    return context;
  }
}
