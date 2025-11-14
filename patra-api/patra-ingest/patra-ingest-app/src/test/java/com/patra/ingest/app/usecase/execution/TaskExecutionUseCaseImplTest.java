package com.patra.ingest.app.usecase.execution;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.complete.CompleteTaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.prepare.PrepareTaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.ExecuteTaskBatchesUseCase;
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

/**
 * TaskExecutionUseCaseImpl 单元测试
 *
 * <p>测试范围:
 *
 * <ul>
 *   <li>✅ 正常流程: 准备 → 执行 → 完成
 *   <li>✅ 幂等跳过: 任务已成功场景
 *   <li>✅ 租约失败: 租约获取失败场景
 *   <li>✅ 异常处理: 各阶段异常的资源清理
 *   <li>✅ 调用顺序验证: 三阶段编排顺序
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("TaskExecutionUseCaseImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskExecutionUseCaseImplTest {

  @Mock private PrepareTaskExecutionUseCase prepareUseCase;
  @Mock private ExecuteTaskBatchesUseCase executeUseCase;
  @Mock private CompleteTaskExecutionUseCase completeUseCase;

  @InjectMocks private TaskExecutionUseCaseImpl taskExecutionUseCase;

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
      PrepareTaskExecutionUseCase.PrepareResult prepareResult =
          new PrepareTaskExecutionUseCase.PrepareResult(mockSession, mockContext);
      when(prepareUseCase.prepare(command)).thenReturn(prepareResult);

      // Mock 执行阶段返回
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(executeUseCase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionUseCase.execute(command);

      // Then: 验证三个阶段都被调用
      InOrder inOrder = inOrder(prepareUseCase, executeUseCase, completeUseCase);
      inOrder.verify(prepareUseCase).prepare(command);
      inOrder.verify(executeUseCase).execute(mockSession, mockContext);
      inOrder.verify(completeUseCase).complete(mockSession, mockContext, executeResult);
    }

    @Test
    @DisplayName("应该正确传递执行结果给完成阶段")
    void shouldPassExecuteResultToCompletePhase() {
      // Given: Mock 准备和执行阶段
      PrepareTaskExecutionUseCase.PrepareResult prepareResult =
          new PrepareTaskExecutionUseCase.PrepareResult(mockSession, mockContext);
      when(prepareUseCase.prepare(command)).thenReturn(prepareResult);

      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(20, 18, 2);
      when(executeUseCase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionUseCase.execute(command);

      // Then: 完成阶段应该接收正确的执行结果
      verify(completeUseCase).complete(mockSession, mockContext, executeResult);
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
      when(prepareUseCase.prepare(command))
          .thenThrow(
              new PrepareTaskExecutionUseCase.TaskAlreadySucceededException(
                  "任务已成功 taskId=" + command.taskId()));

      // When: 执行任务（不应该抛出异常）
      taskExecutionUseCase.execute(command);

      // Then: 执行和完成阶段不应该被调用
      verify(prepareUseCase).prepare(command);
      verifyNoInteractions(executeUseCase);
      verifyNoInteractions(completeUseCase);
    }

    @Test
    @DisplayName("幂等跳过时不应该抛出异常")
    void shouldNotThrowExceptionWhenIdempotentSkip() {
      // Given: 准备阶段抛出任务已成功异常
      when(prepareUseCase.prepare(command))
          .thenThrow(new PrepareTaskExecutionUseCase.TaskAlreadySucceededException("任务已成功"));

      // When & Then: 执行任务不应该抛出异常
      assertThatCode(() -> taskExecutionUseCase.execute(command)).doesNotThrowAnyException();
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
      when(prepareUseCase.prepare(command))
          .thenThrow(
              new PrepareTaskExecutionUseCase.LeaseAcquisitionFailedException(
                  "租约获取失败 taskId=" + command.taskId()));

      // When: 执行任务（不应该抛出异常）
      taskExecutionUseCase.execute(command);

      // Then: 执行和完成阶段不应该被调用
      verify(prepareUseCase).prepare(command);
      verifyNoInteractions(executeUseCase);
      verifyNoInteractions(completeUseCase);
    }

    @Test
    @DisplayName("租约失败时不应该抛出异常")
    void shouldNotThrowExceptionWhenLeaseAcquisitionFails() {
      // Given: 准备阶段抛出租约获取失败异常
      when(prepareUseCase.prepare(command))
          .thenThrow(new PrepareTaskExecutionUseCase.LeaseAcquisitionFailedException("租约失败"));

      // When & Then: 执行任务不应该抛出异常
      assertThatCode(() -> taskExecutionUseCase.execute(command)).doesNotThrowAnyException();
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
      PrepareTaskExecutionUseCase.PrepareResult prepareResult =
          new PrepareTaskExecutionUseCase.PrepareResult(mockSession, mockContext);
      when(prepareUseCase.prepare(command)).thenReturn(prepareResult);
      when(executeUseCase.execute(mockSession, mockContext))
          .thenThrow(new RuntimeException("执行失败"));

      // When & Then: 执行任务应该抛出 TaskExecutionException
      assertThatThrownBy(() -> taskExecutionUseCase.execute(command))
          .isInstanceOf(TaskExecutionUseCaseImpl.TaskExecutionException.class)
          .hasCauseInstanceOf(RuntimeException.class)
          .hasMessageContaining("任务执行失败");

      // 验证会话清理被调用
      verify(mockSession).cleanup();
    }

    @Test
    @DisplayName("当完成阶段失败时应该清理会话资源")
    void shouldCleanupSessionWhenCompletePhaseFails() {
      // Given: 准备和执行阶段成功，完成阶段失败
      PrepareTaskExecutionUseCase.PrepareResult prepareResult =
          new PrepareTaskExecutionUseCase.PrepareResult(mockSession, mockContext);
      when(prepareUseCase.prepare(command)).thenReturn(prepareResult);

      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(executeUseCase.execute(mockSession, mockContext)).thenReturn(executeResult);

      doThrow(new RuntimeException("完成失败"))
          .when(completeUseCase)
          .complete(mockSession, mockContext, executeResult);

      // When & Then: 执行任务应该抛出 TaskExecutionException
      assertThatThrownBy(() -> taskExecutionUseCase.execute(command))
          .isInstanceOf(TaskExecutionUseCaseImpl.TaskExecutionException.class)
          .hasCauseInstanceOf(RuntimeException.class);

      // 验证会话清理被调用
      verify(mockSession).cleanup();
    }

    @Test
    @DisplayName("当会话清理失败时不应该影响异常传播")
    void shouldNotSuppressExceptionWhenSessionCleanupFails() {
      // Given: 执行失败且会话清理也失败
      PrepareTaskExecutionUseCase.PrepareResult prepareResult =
          new PrepareTaskExecutionUseCase.PrepareResult(mockSession, mockContext);
      when(prepareUseCase.prepare(command)).thenReturn(prepareResult);
      when(executeUseCase.execute(mockSession, mockContext))
          .thenThrow(new RuntimeException("执行失败"));
      doThrow(new RuntimeException("清理失败")).when(mockSession).cleanup();

      // When & Then: 仍然应该抛出原始的执行异常
      assertThatThrownBy(() -> taskExecutionUseCase.execute(command))
          .isInstanceOf(TaskExecutionUseCaseImpl.TaskExecutionException.class)
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
      when(prepareUseCase.prepare(command)).thenThrow(new RuntimeException("准备失败"));

      // When & Then: 执行任务应该抛出异常
      assertThatThrownBy(() -> taskExecutionUseCase.execute(command))
          .isInstanceOf(TaskExecutionUseCaseImpl.TaskExecutionException.class);

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
      PrepareTaskExecutionUseCase.PrepareResult prepareResult =
          new PrepareTaskExecutionUseCase.PrepareResult(mockSession, mockContext);
      when(prepareUseCase.prepare(command)).thenReturn(prepareResult);

      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(0, 0, 0);
      when(executeUseCase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionUseCase.execute(command);

      // Then: 完成阶段仍应该被调用
      verify(completeUseCase).complete(mockSession, mockContext, executeResult);
    }

    @Test
    @DisplayName("应该支持部分失败的执行结果")
    void shouldSupportPartialFailureExecuteResult() {
      // Given: 执行阶段返回部分失败
      PrepareTaskExecutionUseCase.PrepareResult prepareResult =
          new PrepareTaskExecutionUseCase.PrepareResult(mockSession, mockContext);
      when(prepareUseCase.prepare(command)).thenReturn(prepareResult);

      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 7, 3);
      when(executeUseCase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionUseCase.execute(command);

      // Then: 完成阶段应该接收部分失败的结果
      verify(completeUseCase).complete(mockSession, mockContext, executeResult);
    }

    @Test
    @DisplayName("应该支持全部失败的执行结果")
    void shouldSupportCompleteFailureExecuteResult() {
      // Given: 执行阶段返回全部失败
      PrepareTaskExecutionUseCase.PrepareResult prepareResult =
          new PrepareTaskExecutionUseCase.PrepareResult(mockSession, mockContext);
      when(prepareUseCase.prepare(command)).thenReturn(prepareResult);

      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(5, 0, 5);
      when(executeUseCase.execute(mockSession, mockContext)).thenReturn(executeResult);

      // When: 执行任务
      taskExecutionUseCase.execute(command);

      // Then: 完成阶段应该接收全部失败的结果
      verify(completeUseCase).complete(mockSession, mockContext, executeResult);
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
