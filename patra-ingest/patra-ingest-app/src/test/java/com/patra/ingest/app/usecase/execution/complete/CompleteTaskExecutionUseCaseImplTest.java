package com.patra.ingest.app.usecase.execution.complete;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.execution.cursor.CursorAdvancer;
import com.patra.ingest.app.usecase.execution.lease.LeaseManagementService;
import com.patra.ingest.app.usecase.execution.publisher.LiteratureEventPublisher;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.ExecuteTaskBatchesUseCase;
import com.patra.ingest.domain.event.LiteratureDataReadyEvent;
import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.batch.BatchStats;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * CompleteTaskExecutionUseCaseImpl 单元测试
 *
 * <p>测试范围:
 *
 * <ul>
 *   <li>✅ 全部成功场景: 游标推进成功 → Task/TaskRun: SUCCEEDED
 *   <li>✅ 全部成功但游标失败: Task: FAILED, TaskRun: PARTIAL
 *   <li>✅ 部分成功场景: Task: FAILED, TaskRun: PARTIAL
 *   <li>✅ 全部失败场景: Task: FAILED, TaskRun: FAILED
 *   <li>✅ 乐观锁冲突: 游标推进失败时的处理
 *   <li>✅ 资源清理: 心跳停止、租约释放
 *   <li>✅ 事件发布: TaskCompletedEvent、LiteratureDataReadyEvent
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("CompleteTaskExecutionUseCaseImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompleteTaskExecutionUseCaseImplTest {

  @Mock private TaskRepository taskRepository;
  @Mock private TaskRunRepository taskRunRepository;
  @Mock private TaskRunBatchRepository taskRunBatchRepository;
  @Mock private CursorAdvancer cursorAdvancer;
  @Mock private LeaseManagementService leaseManagementService;
  @Mock private LiteratureEventPublisher literatureEventPublisher;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private Clock clock;

  @InjectMocks private CompleteTaskExecutionUseCaseImpl completeUseCase;

  private ExecutionSession mockSession;
  private ExecutionContext mockContext;
  private TaskAggregate mockTask;
  private TaskRun mockTaskRun;
  private Instant fixedNow;

  @BeforeEach
  void setUp() {
    fixedNow = Instant.parse("2025-01-06T10:00:00Z");
    when(clock.instant()).thenReturn(fixedNow);
    when(clock.getZone()).thenReturn(ZoneId.systemDefault());

    mockSession = createMockSession();
    mockContext = createMockContext();
    mockTask = createMockTask();
    mockTaskRun = createMockTaskRun();

    when(taskRepository.findById(mockSession.taskId())).thenReturn(Optional.of(mockTask));
    when(taskRunRepository.findById(mockSession.runId())).thenReturn(Optional.of(mockTaskRun));
  }

  // ========== 全部成功场景 ==========

  @Nested
  @DisplayName("全部成功场景")
  class AllSuccessTests {

    @Test
    @DisplayName("全部批次成功且游标推进成功时应该标记为 SUCCEEDED")
    void shouldMarkAsSucceededWhenAllBatchesSucceedAndCursorAdvances() {
      // Given: 全部批次成功
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenReturn(true);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: Task 和 TaskRun 应该被标记为 SUCCEEDED
      verify(mockTask).markSucceeded(fixedNow);
      verify(mockTaskRun).succeed(fixedNow);
      verify(taskRepository).save(mockTask);
      verify(taskRunRepository).save(mockTaskRun);
    }

    @Test
    @DisplayName("全部成功时应该推进游标")
    void shouldAdvanceCursorWhenAllBatchesSucceed() {
      // Given: 全部批次成功
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenReturn(true);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 应该调用游标推进
      verify(cursorAdvancer)
          .advance(mockContext, mockSession.taskId(), mockSession.runId(), 9001L);
    }

    @Test
    @DisplayName("全部成功但游标推进失败时应该标记 Task 为 FAILED，TaskRun 为 PARTIAL")
    void shouldMarkAsFailedAndPartialWhenCursorAdvancementFails() {
      // Given: 全部批次成功，但游标推进失败
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenReturn(false);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: Task: FAILED, TaskRun: PARTIAL
      verify(mockTask).markFailed(fixedNow);
      verify(mockTaskRun).markPartial("Cursor advancement failed", fixedNow);
      verify(taskRepository).save(mockTask);
      verify(taskRunRepository).save(mockTaskRun);
    }

    @Test
    @DisplayName("游标推进遇到乐观锁冲突时应该标记 Task 为 FAILED，TaskRun 为 PARTIAL")
    void shouldMarkAsFailedAndPartialWhenOptimisticLockConflict() {
      // Given: 游标推进遇到乐观锁冲突
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenThrow(new OptimisticLockingFailureException("version mismatch"));
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: Task: FAILED, TaskRun: PARTIAL (可重试)
      verify(mockTask).markFailed(fixedNow);
      verify(mockTaskRun).markPartial("Cursor advancement failed", fixedNow);
    }
  }

  // ========== 部分成功场景 ==========

  @Nested
  @DisplayName("部分成功场景")
  class PartialSuccessTests {

    @Test
    @DisplayName("部分批次成功时应该标记 Task 为 FAILED，TaskRun 为 PARTIAL")
    void shouldMarkAsFailedAndPartialWhenPartialSuccess() {
      // Given: 部分批次成功
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 7, 3);
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: Task: FAILED, TaskRun: PARTIAL
      verify(mockTask).markFailed(fixedNow);
      verify(mockTaskRun).markPartial("Some batches failed", fixedNow);
      verify(taskRepository).save(mockTask);
      verify(taskRunRepository).save(mockTaskRun);
    }

    @Test
    @DisplayName("部分成功时不应该推进游标")
    void shouldNotAdvanceCursorWhenPartialSuccess() {
      // Given: 部分批次成功
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 7, 3);
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 不应该调用游标推进
      verifyNoInteractions(cursorAdvancer);
    }

    @Test
    @DisplayName("部分成功时应该发布文献数据就绪事件")
    void shouldPublishLiteratureEventWhenPartialSuccess() {
      // Given: 部分批次成功，有成功的批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(5, 3, 2);

      List<TaskRunBatch> batches = createMockBatches(3, 2);
      when(taskRunBatchRepository.findByRunId(mockSession.runId())).thenReturn(batches);

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 应该发布文献数据就绪事件
      ArgumentCaptor<LiteratureDataReadyEvent> eventCaptor =
          ArgumentCaptor.forClass(LiteratureDataReadyEvent.class);
      verify(literatureEventPublisher).publish(eventCaptor.capture());

      LiteratureDataReadyEvent event = eventCaptor.getValue();
      assertThat(event.taskId()).isEqualTo(mockSession.taskId());
      assertThat(event.runId()).isEqualTo(mockSession.runId());
      assertThat(event.successBatchCount()).isEqualTo(3);
      assertThat(event.failedBatchCount()).isEqualTo(2);
    }
  }

  // ========== 全部失败场景 ==========

  @Nested
  @DisplayName("全部失败场景")
  class AllFailureTests {

    @Test
    @DisplayName("全部批次失败时应该标记 Task 和 TaskRun 为 FAILED")
    void shouldMarkAsFailedWhenAllBatchesFail() {
      // Given: 全部批次失败
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(5, 0, 5);
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: Task 和 TaskRun 都应该为 FAILED
      verify(mockTask).markFailed(fixedNow);
      verify(mockTaskRun).fail("All batches failed", fixedNow);
      verify(taskRepository).save(mockTask);
      verify(taskRunRepository).save(mockTaskRun);
    }

    @Test
    @DisplayName("无批次执行时应该标记 Task 和 TaskRun 为 FAILED")
    void shouldMarkAsFailedWhenNoBatchesExecuted() {
      // Given: 无批次执行
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(0, 0, 0);
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: Task 和 TaskRun 都应该为 FAILED
      verify(mockTask).markFailed(fixedNow);
      verify(mockTaskRun).fail("All batches failed", fixedNow);
    }

    @Test
    @DisplayName("全部失败时不应该推进游标")
    void shouldNotAdvanceCursorWhenAllBatchesFail() {
      // Given: 全部批次失败
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(5, 0, 5);
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 不应该调用游标推进
      verifyNoInteractions(cursorAdvancer);
    }

    @Test
    @DisplayName("全部失败时不应该发布文献数据就绪事件")
    void shouldNotPublishLiteratureEventWhenAllBatchesFail() {
      // Given: 全部批次失败
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(5, 0, 5);
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 不应该发布文献数据就绪事件
      verifyNoInteractions(literatureEventPublisher);
    }
  }

  // ========== 资源清理测试 ==========

  @Nested
  @DisplayName("资源清理")
  class ResourceCleanupTests {

    @Test
    @DisplayName("完成后应该停止心跳并释放租约")
    void shouldStopHeartbeatAndReleaseLeaseAfterCompletion() {
      // Given: 成功场景
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenReturn(true);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 应该清理资源
      verify(mockSession).cleanup();
      verify(leaseManagementService).releaseLease(mockSession.taskId());
    }

    @Test
    @DisplayName("失败场景也应该清理资源")
    void shouldCleanupResourcesEvenWhenFailed() {
      // Given: 失败场景
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(5, 0, 5);
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 应该清理资源
      verify(mockSession).cleanup();
      verify(leaseManagementService).releaseLease(mockSession.taskId());
    }

    @Test
    @DisplayName("资源清理失败时不应该影响完成流程")
    void shouldNotAffectCompletionWhenCleanupFails() {
      // Given: 资源清理失败
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenReturn(true);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      doThrow(new RuntimeException("cleanup failed")).when(mockSession).cleanup();

      // When: 完成任务（不应该抛出异常）
      assertThatCode(() -> completeUseCase.complete(mockSession, mockContext, executeResult))
          .doesNotThrowAnyException();

      // Then: Task 和 TaskRun 仍应该被保存
      verify(taskRepository).save(mockTask);
      verify(taskRunRepository).save(mockTaskRun);
    }

    @Test
    @DisplayName("应该在最后清理资源（finally 块）")
    void shouldCleanupResourcesInFinallyBlock() {
      // Given: 完成阶段抛出异常
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(taskRepository.findById(mockSession.taskId()))
          .thenThrow(new RuntimeException("repository error"));

      // When: 完成任务（会抛出异常）
      assertThatThrownBy(() -> completeUseCase.complete(mockSession, mockContext, executeResult))
          .isInstanceOf(RuntimeException.class);

      // Then: 资源清理仍应该被调用
      verify(mockSession).cleanup();
      verify(leaseManagementService).releaseLease(mockSession.taskId());
    }
  }

  // ========== 事件发布测试 ==========

  @Nested
  @DisplayName("事件发布")
  class EventPublishingTests {

    @Test
    @DisplayName("Task 成功时应该发布 TaskCompletedEvent")
    void shouldPublishTaskCompletedEventWhenTaskSucceeds() {
      // Given: 成功场景
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenReturn(true);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      TaskCompletedEvent domainEvent = mock(TaskCompletedEvent.class);
      when(mockTask.pullDomainEvents()).thenReturn(List.of(domainEvent));

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 应该发布领域事件
      verify(applicationEventPublisher).publishEvent(domainEvent);
    }

    @Test
    @DisplayName("有成功批次时应该发布 LiteratureDataReadyEvent")
    void shouldPublishLiteratureDataReadyEventWhenBatchesSucceed() {
      // Given: 有成功批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenReturn(true);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));

      List<TaskRunBatch> batches = createMockBatches(10, 0);
      when(taskRunBatchRepository.findByRunId(mockSession.runId())).thenReturn(batches);

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 应该发布文献数据就绪事件
      verify(literatureEventPublisher).publish(any(LiteratureDataReadyEvent.class));
    }

    @Test
    @DisplayName("LiteratureDataReadyEvent 应该包含正确的统计信息")
    void shouldIncludeCorrectStatsInLiteratureDataReadyEvent() {
      // Given: 成功场景
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(cursorAdvancer.advance(eq(mockContext), anyLong(), anyLong(), anyLong()))
          .thenReturn(true);
      when(taskRunBatchRepository.findLastSucceededBatchId(mockSession.runId()))
          .thenReturn(Optional.of(9001L));

      List<TaskRunBatch> batches = createMockBatches(10, 0);
      when(taskRunBatchRepository.findByRunId(mockSession.runId())).thenReturn(batches);

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 验证事件内容
      ArgumentCaptor<LiteratureDataReadyEvent> eventCaptor =
          ArgumentCaptor.forClass(LiteratureDataReadyEvent.class);
      verify(literatureEventPublisher).publish(eventCaptor.capture());

      LiteratureDataReadyEvent event = eventCaptor.getValue();
      assertThat(event.taskId()).isEqualTo(mockSession.taskId());
      assertThat(event.runId()).isEqualTo(mockSession.runId());
      assertThat(event.provenanceCode()).isEqualTo(mockContext.provenanceCode());
      assertThat(event.successBatchCount()).isEqualTo(10);
      assertThat(event.failedBatchCount()).isEqualTo(0);
      assertThat(event.totalLiteratureCount()).isEqualTo(1000); // 10 batches * 100 records
    }
  }

  // ========== 批次统计聚合测试 ==========

  @Nested
  @DisplayName("批次统计聚合")
  class BatchStatsAggregationTests {

    @Test
    @DisplayName("应该聚合批次统计到 TaskRun")
    void shouldAggregateBatchStatsToTaskRun() {
      // Given: 有批次数据
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(5, 4, 1);

      List<TaskRunBatch> batches = createMockBatches(4, 1);
      when(taskRunBatchRepository.findByRunId(mockSession.runId())).thenReturn(batches);

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 应该调用 appendStats
      verify(mockTaskRun).appendStats(argThat(stats -> stats.fetched() == 400L && stats.failed() == 1L));
    }

    @Test
    @DisplayName("无批次时不应该聚合统计")
    void shouldNotAggregateStatsWhenNoBatches() {
      // Given: 无批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(0, 0, 0);
      when(taskRunBatchRepository.findByRunId(mockSession.runId()))
          .thenReturn(Collections.emptyList());

      // When: 完成任务
      completeUseCase.complete(mockSession, mockContext, executeResult);

      // Then: 不应该调用 appendStats
      verify(mockTaskRun, never()).appendStats(any());
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
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(taskRepository.findById(mockSession.taskId())).thenReturn(Optional.empty());

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> completeUseCase.complete(mockSession, mockContext, executeResult))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Task not found");

      // 资源清理仍应该被调用
      verify(mockSession).cleanup();
      verify(leaseManagementService).releaseLease(mockSession.taskId());
    }

    @Test
    @DisplayName("当 TaskRun 未找到时应该抛出异常")
    void shouldThrowExceptionWhenTaskRunNotFound() {
      // Given: TaskRun 不存在
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          new ExecuteTaskBatchesUseCase.ExecuteResult(10, 10, 0);
      when(taskRunRepository.findById(mockSession.runId())).thenReturn(Optional.empty());

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> completeUseCase.complete(mockSession, mockContext, executeResult))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Run record not found");

      // 资源清理仍应该被调用
      verify(mockSession).cleanup();
      verify(leaseManagementService).releaseLease(mockSession.taskId());
    }
  }

  // ========== 辅助方法 ==========

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
    when(context.taskId()).thenReturn(1001L);
    when(context.runId()).thenReturn(2001L);
    when(context.provenanceCode()).thenReturn(ProvenanceCode.PUBMED);
    when(context.operationCode()).thenReturn("harvest");
    return context;
  }

  private TaskAggregate createMockTask() {
    TaskAggregate task = mock(TaskAggregate.class);
    when(task.getId()).thenReturn(1001L);
    when(task.getStatus()).thenReturn(TaskStatus.RUNNING);
    when(task.pullDomainEvents()).thenReturn(Collections.emptyList());
    doNothing().when(task).markSucceeded(any(Instant.class));
    doNothing().when(task).markFailed(any(Instant.class));
    return task;
  }

  private TaskRun createMockTaskRun() {
    TaskRun taskRun = mock(TaskRun.class);
    doNothing().when(taskRun).succeed(any(Instant.class));
    doNothing().when(taskRun).fail(anyString(), any(Instant.class));
    doNothing().when(taskRun).markPartial(anyString(), any(Instant.class));
    doNothing().when(taskRun).appendStats(any());
    return taskRun;
  }

  private List<TaskRunBatch> createMockBatches(int successCount, int failCount) {
    List<TaskRunBatch> batches = new java.util.ArrayList<>();

    // 成功批次
    for (int i = 0; i < successCount; i++) {
      TaskRunBatch batch = mock(TaskRunBatch.class);
      when(batch.getStatus()).thenReturn(BatchStatus.SUCCEEDED);
      when(batch.getStorageKey()).thenReturn("storage-key-" + i);
      when(batch.getStats()).thenReturn(new BatchStats(100));
      batches.add(batch);
    }

    // 失败批次
    for (int i = 0; i < failCount; i++) {
      TaskRunBatch batch = mock(TaskRunBatch.class);
      when(batch.getStatus()).thenReturn(BatchStatus.FAILED);
      when(batch.getStorageKey()).thenReturn(null);
      when(batch.getStats()).thenReturn(null);
      batches.add(batch);
    }

    return batches;
  }
}
