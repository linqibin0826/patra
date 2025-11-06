package com.patra.ingest.app.usecase.execution.strategy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.patra.ingest.app.usecase.execution.coordination.GenericBatchExecutor;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.planner.BatchPlanner;
import com.patra.ingest.app.usecase.execution.strategy.planner.BatchPlannerRegistry;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchPlan;
import com.patra.ingest.domain.model.vo.batch.BatchResult;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ExecuteTaskBatchesUseCaseImpl 单元测试
 *
 * <p>测试范围:
 *
 * <ul>
 *   <li>✅ 正常流程: 批次规划 → 批次执行 → 持久化结果
 *   <li>✅ 多批次执行: 顺序执行多个批次
 *   <li>✅ 批次执行失败: 某个批次失败，记录错误继续
 *   <li>✅ 全部批次失败: 所有批次都失败
 *   <li>✅ 空批次列表: 没有批次需要执行
 *   <li>✅ 批次限制检查: 超出限制时抛出异常
 *   <li>✅ 租约撤销检查: 租约撤销时中止执行
 *   <li>✅ 快速失败模式: fail-fast 配置测试
 *   <li>✅ 心跳更新: 批次执行后更新心跳
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("ExecuteTaskBatchesUseCaseImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecuteTaskBatchesUseCaseImplTest {

  @Mock private BatchPlannerRegistry plannerRegistry;
  @Mock private GenericBatchExecutor batchExecutor;
  @Mock private TaskRunBatchRepository batchRepository;
  @Mock private TaskRunRepository taskRunRepository;

  @InjectMocks private ExecuteTaskBatchesUseCaseImpl executeUseCase;

  private ExecutionSession mockSession;
  private ExecutionContext mockContext;
  private BatchPlanner mockPlanner;
  private ExecutionSession.HeartbeatHandle mockHeartbeatHandle;

  @BeforeEach
  void setUp() {
    mockSession = createMockSession();
    mockContext = createMockContext();
    mockPlanner = mock(BatchPlanner.class);
    mockHeartbeatHandle = mock(ExecutionSession.HeartbeatHandle.class);

    // 默认配置：非快速失败模式
    ReflectionTestUtils.setField(executeUseCase, "failFast", false);

    // 默认行为：BatchPlannerRegistry 返回 planner
    when(plannerRegistry.get(mockContext.provenanceCode())).thenReturn(mockPlanner);
  }

  // ========== 正常流程测试 ==========

  @Nested
  @DisplayName("正常流程")
  class HappyPathTests {

    @Test
    @DisplayName("应该成功执行单个批次")
    void shouldExecuteSingleBatchSuccessfully() {
      // Given: 创建单批次计划
      Batch batch1 = createBatch(1, 1);
      BatchPlan plan = createBatchPlan(List.of(batch1));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // Mock 批次执行器返回成功结果
      BatchResult result1 = BatchResult.success(1, 100, null, null);
      when(batchExecutor.execute(mockContext, batch1)).thenReturn(result1);

      // Mock 心跳更新成功
      when(taskRunRepository.touchHeartbeat(eq(mockSession.runId()), any(Instant.class)))
          .thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 验证结果统计
      assertThat(executeResult.totalBatches()).isEqualTo(1);
      assertThat(executeResult.succeededBatches()).isEqualTo(1);
      assertThat(executeResult.failedBatches()).isEqualTo(0);

      // 验证批次执行器被调用
      verify(batchExecutor).execute(mockContext, batch1);

      // 验证批次结果被持久化
      verify(batchRepository).save(any(TaskRunBatch.class));

      // 验证心跳更新被调用
      verify(taskRunRepository).touchHeartbeat(eq(mockSession.runId()), any(Instant.class));
    }

    @Test
    @DisplayName("应该顺序执行多个批次")
    void shouldExecuteMultipleBatchesSequentially() {
      // Given: 创建三批次计划
      Batch batch1 = createBatch(1, 3);
      Batch batch2 = createBatch(2, 3);
      Batch batch3 = createBatch(3, 3);
      BatchPlan plan = createBatchPlan(List.of(batch1, batch2, batch3));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // Mock 批次执行器返回成功结果
      when(batchExecutor.execute(eq(mockContext), any(Batch.class)))
          .thenReturn(BatchResult.success(1, 50, null, null))
          .thenReturn(BatchResult.success(2, 60, null, null))
          .thenReturn(BatchResult.success(3, 70, null, null));

      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class))).thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 验证结果统计
      assertThat(executeResult.totalBatches()).isEqualTo(3);
      assertThat(executeResult.succeededBatches()).isEqualTo(3);
      assertThat(executeResult.failedBatches()).isEqualTo(0);

      // 验证批次执行器按顺序调用
      verify(batchExecutor).execute(mockContext, batch1);
      verify(batchExecutor).execute(mockContext, batch2);
      verify(batchExecutor).execute(mockContext, batch3);

      // 验证持久化 3 次
      verify(batchRepository, times(3)).save(any(TaskRunBatch.class));

      // 验证心跳更新 3 次
      verify(taskRunRepository, times(3)).touchHeartbeat(eq(mockSession.runId()), any(Instant.class));
    }
  }

  // ========== 批次失败场景 ==========

  @Nested
  @DisplayName("批次失败场景")
  class BatchFailureTests {

    @Test
    @DisplayName("某个批次失败时应该记录错误并继续执行")
    void shouldRecordFailureAndContinueWhenBatchFails() {
      // Given: 创建三批次计划，第二个批次失败
      Batch batch1 = createBatch(1, 3);
      Batch batch2 = createBatch(2, 3);
      Batch batch3 = createBatch(3, 3);
      BatchPlan plan = createBatchPlan(List.of(batch1, batch2, batch3));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // Mock 批次执行：第二个批次抛出异常
      when(batchExecutor.execute(mockContext, batch1)).thenReturn(BatchResult.success(1, 50, null, null));
      when(batchExecutor.execute(mockContext, batch2))
          .thenThrow(new RuntimeException("API 超时"));
      when(batchExecutor.execute(mockContext, batch3)).thenReturn(BatchResult.success(3, 70, null, null));

      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class))).thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 验证结果统计
      assertThat(executeResult.totalBatches()).isEqualTo(3);
      assertThat(executeResult.succeededBatches()).isEqualTo(2);
      assertThat(executeResult.failedBatches()).isEqualTo(1);

      // 验证所有批次都尝试执行
      verify(batchExecutor).execute(mockContext, batch1);
      verify(batchExecutor).execute(mockContext, batch2);
      verify(batchExecutor).execute(mockContext, batch3);

      // 验证持久化 3 次（包括失败的批次）
      verify(batchRepository, times(3)).save(any(TaskRunBatch.class));
    }

    @Test
    @DisplayName("所有批次失败时应该返回全部失败统计")
    void shouldReturnAllFailedWhenAllBatchesFail() {
      // Given: 创建两批次计划，都失败
      Batch batch1 = createBatch(1, 2);
      Batch batch2 = createBatch(2, 2);
      BatchPlan plan = createBatchPlan(List.of(batch1, batch2));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // Mock 批次执行：都抛出异常
      when(batchExecutor.execute(eq(mockContext), any(Batch.class)))
          .thenThrow(new RuntimeException("网络错误"));

      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class))).thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 验证结果统计
      assertThat(executeResult.totalBatches()).isEqualTo(2);
      assertThat(executeResult.succeededBatches()).isEqualTo(0);
      assertThat(executeResult.failedBatches()).isEqualTo(2);

      // 验证持久化 2 次
      verify(batchRepository, times(2)).save(any(TaskRunBatch.class));
    }

    @Test
    @DisplayName("快速失败模式下第一个批次失败应该中止后续执行")
    void shouldAbortOnFirstFailureWhenFailFastEnabled() {
      // Given: 启用快速失败模式
      ReflectionTestUtils.setField(executeUseCase, "failFast", true);

      Batch batch1 = createBatch(1, 3);
      Batch batch2 = createBatch(2, 3);
      Batch batch3 = createBatch(3, 3);
      BatchPlan plan = createBatchPlan(List.of(batch1, batch2, batch3));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // Mock 批次执行：第一个批次失败
      when(batchExecutor.execute(mockContext, batch1))
          .thenThrow(new RuntimeException("第一批次失败"));

      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class))).thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 验证只执行了第一个批次
      assertThat(executeResult.totalBatches()).isEqualTo(3);
      assertThat(executeResult.succeededBatches()).isEqualTo(0);
      assertThat(executeResult.failedBatches()).isEqualTo(1);

      // 验证只调用了第一个批次
      verify(batchExecutor, times(1)).execute(eq(mockContext), any(Batch.class));
      verify(batchExecutor).execute(mockContext, batch1);
      verify(batchExecutor, never()).execute(mockContext, batch2);
      verify(batchExecutor, never()).execute(mockContext, batch3);
    }
  }

  // ========== 空批次和限制检查 ==========

  @Nested
  @DisplayName("空批次和限制检查")
  class EmptyBatchAndLimitTests {

    @Test
    @DisplayName("当没有批次时应该返回零统计结果")
    void shouldReturnZeroStatsWhenNoBatches() {
      // Given: 创建空批次计划
      BatchPlan plan = createBatchPlan(List.of());
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 验证结果为零
      assertThat(executeResult.totalBatches()).isEqualTo(0);
      assertThat(executeResult.succeededBatches()).isEqualTo(0);
      assertThat(executeResult.failedBatches()).isEqualTo(0);

      // 验证批次执行器从未被调用
      verifyNoInteractions(batchExecutor);
      verifyNoInteractions(batchRepository);
    }

    @Test
    @DisplayName("当批次数量超过限制时应该抛出异常")
    void shouldThrowExceptionWhenBatchLimitExceeded() {
      // Given: 创建超限的批次计划
      BatchPlan plan = mock(BatchPlan.class);
      when(plan.exceedsLimit()).thenReturn(true);
      when(plan.totalBatches()).thenReturn(10000);
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // When & Then: 执行批次应该抛出异常
      assertThatThrownBy(() -> executeUseCase.execute(mockSession, mockContext))
          .isInstanceOf(ExecuteTaskBatchesUseCaseImpl.BatchLimitExceededException.class)
          .hasMessageContaining("批次数量超过限制")
          .hasMessageContaining("taskId=" + mockSession.taskId())
          .hasMessageContaining("totalBatches=10000");

      // 验证批次执行器从未被调用
      verifyNoInteractions(batchExecutor);
    }
  }

  // ========== 租约撤销检查 ==========

  @Nested
  @DisplayName("租约撤销检查")
  class LeaseRevocationTests {

    @Test
    @DisplayName("当租约被撤销时应该立即中止后续批次执行")
    void shouldAbortWhenLeaseIsRevoked() {
      // Given: 创建三批次计划
      Batch batch1 = createBatch(1, 3);
      Batch batch2 = createBatch(2, 3);
      Batch batch3 = createBatch(3, 3);
      BatchPlan plan = createBatchPlan(List.of(batch1, batch2, batch3));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // Mock 租约在第二个批次前被撤销
      when(mockHeartbeatHandle.isLeaseRevoked()).thenReturn(false).thenReturn(true);
      when(mockSession.heartbeatHandle()).thenReturn(mockHeartbeatHandle);

      // Mock 批次执行器
      when(batchExecutor.execute(mockContext, batch1)).thenReturn(BatchResult.success(1, 50, null, null));

      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class))).thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 只执行了第一个批次
      assertThat(executeResult.totalBatches()).isEqualTo(3);
      assertThat(executeResult.succeededBatches()).isEqualTo(1);
      assertThat(executeResult.failedBatches()).isEqualTo(0);

      // 验证只执行了第一个批次
      verify(batchExecutor, times(1)).execute(eq(mockContext), any(Batch.class));
      verify(batchExecutor).execute(mockContext, batch1);
      verify(batchExecutor, never()).execute(mockContext, batch2);
      verify(batchExecutor, never()).execute(mockContext, batch3);
    }

    @Test
    @DisplayName("当心跳句柄为空时应该正常执行所有批次")
    void shouldExecuteAllBatchesWhenHeartbeatHandleIsNull() {
      // Given: 创建会话时心跳句柄为 null
      ExecutionSession sessionWithoutHeartbeat =
          new ExecutionSession(1001L, 2001L, "worker-1", null, false);

      Batch batch1 = createBatch(1, 2);
      Batch batch2 = createBatch(2, 2);
      BatchPlan plan = createBatchPlan(List.of(batch1, batch2));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      when(batchExecutor.execute(eq(mockContext), any(Batch.class)))
          .thenReturn(BatchResult.success(1, 50, null, null))
          .thenReturn(BatchResult.success(2, 60, null, null));

      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class))).thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(sessionWithoutHeartbeat, mockContext);

      // Then: 验证所有批次都执行
      assertThat(executeResult.totalBatches()).isEqualTo(2);
      assertThat(executeResult.succeededBatches()).isEqualTo(2);
      assertThat(executeResult.failedBatches()).isEqualTo(0);

      verify(batchExecutor, times(2)).execute(eq(mockContext), any(Batch.class));
    }
  }

  // ========== 心跳更新测试 ==========

  @Nested
  @DisplayName("心跳更新")
  class HeartbeatUpdateTests {

    @Test
    @DisplayName("批次执行后应该更新 TaskRun 心跳")
    void shouldUpdateHeartbeatAfterEachBatch() {
      // Given: 创建单批次计划
      Batch batch1 = createBatch(1, 1);
      BatchPlan plan = createBatchPlan(List.of(batch1));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      when(batchExecutor.execute(mockContext, batch1)).thenReturn(BatchResult.success(1, 100, null, null));
      when(taskRunRepository.touchHeartbeat(eq(mockSession.runId()), any(Instant.class)))
          .thenReturn(true);

      // When: 执行批次
      executeUseCase.execute(mockSession, mockContext);

      // Then: 验证心跳更新被调用
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(taskRunRepository).touchHeartbeat(eq(mockSession.runId()), instantCaptor.capture());

      // 验证时间戳是最近的
      Instant capturedInstant = instantCaptor.getValue();
      assertThat(capturedInstant).isNotNull();
      assertThat(capturedInstant).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("心跳更新失败时不应该影响批次执行")
    void shouldContinueExecutionWhenHeartbeatUpdateFails() {
      // Given: 创建单批次计划
      Batch batch1 = createBatch(1, 1);
      BatchPlan plan = createBatchPlan(List.of(batch1));
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      when(batchExecutor.execute(mockContext, batch1)).thenReturn(BatchResult.success(1, 100, null, null));
      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class)))
          .thenThrow(new RuntimeException("数据库连接失败"));

      // When: 执行批次（不应该抛出异常）
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 批次仍然成功
      assertThat(executeResult.succeededBatches()).isEqualTo(1);
      assertThat(executeResult.failedBatches()).isEqualTo(0);

      // 验证批次结果仍然被持久化
      verify(batchRepository).save(any(TaskRunBatch.class));
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件")
  class EdgeCaseTests {

    @Test
    @DisplayName("应该支持大批量批次执行")
    void shouldSupportLargeBatchExecution() {
      // Given: 创建 100 个批次
      List<Batch> batches =
          java.util.stream.IntStream.rangeClosed(1, 100)
              .mapToObj(i -> createBatch(i, 100))
              .toList();
      BatchPlan plan = createBatchPlan(batches);
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // Mock 批次执行器返回成功结果
      when(batchExecutor.execute(eq(mockContext), any(Batch.class)))
          .thenAnswer(
              invocation -> {
                Batch batch = invocation.getArgument(1);
                return BatchResult.success(batch.batchNo(), 10, null, null);
              });

      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class))).thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 验证所有批次都执行
      assertThat(executeResult.totalBatches()).isEqualTo(100);
      assertThat(executeResult.succeededBatches()).isEqualTo(100);
      assertThat(executeResult.failedBatches()).isEqualTo(0);

      // 验证持久化 100 次
      verify(batchRepository, times(100)).save(any(TaskRunBatch.class));
    }

    @Test
    @DisplayName("应该支持部分成功部分失败的混合场景")
    void shouldSupportMixedSuccessAndFailure() {
      // Given: 创建 5 个批次，奇数成功，偶数失败
      List<Batch> batches =
          java.util.stream.IntStream.rangeClosed(1, 5)
              .mapToObj(i -> createBatch(i, 5))
              .toList();
      BatchPlan plan = createBatchPlan(batches);
      when(mockPlanner.plan(mockContext)).thenReturn(plan);

      // Mock 批次执行器：奇数成功，偶数失败
      when(batchExecutor.execute(eq(mockContext), any(Batch.class)))
          .thenAnswer(
              invocation -> {
                Batch batch = invocation.getArgument(1);
                if (batch.batchNo() % 2 == 1) {
                  return BatchResult.success(batch.batchNo(), 20, null, null);
                } else {
                  throw new RuntimeException("偶数批次失败");
                }
              });

      when(taskRunRepository.touchHeartbeat(anyLong(), any(Instant.class))).thenReturn(true);

      // When: 执行批次
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(mockSession, mockContext);

      // Then: 验证统计正确
      assertThat(executeResult.totalBatches()).isEqualTo(5);
      assertThat(executeResult.succeededBatches()).isEqualTo(3); // 1, 3, 5
      assertThat(executeResult.failedBatches()).isEqualTo(2); // 2, 4

      // 验证持久化 5 次
      verify(batchRepository, times(5)).save(any(TaskRunBatch.class));
    }
  }

  // ========== 辅助方法 ==========

  private ExecutionSession createMockSession() {
    ExecutionSession session = mock(ExecutionSession.class);
    when(session.taskId()).thenReturn(1001L);
    when(session.runId()).thenReturn(2001L);
    when(session.leaseOwner()).thenReturn("worker-1");
    when(session.heartbeatHandle()).thenReturn(null); // 默认无心跳句柄
    return session;
  }

  private ExecutionContext createMockContext() {
    ExecutionContext context = mock(ExecutionContext.class);
    when(context.provenanceCode()).thenReturn("pubmed");
    when(context.operationCode()).thenReturn("harvest");
    return context;
  }

  private Batch createBatch(int batchNo, int totalBatches) {
    Batch batch = mock(Batch.class, "batch" + batchNo);
    when(batch.batchNo()).thenReturn(batchNo);
    return batch;
  }

  private BatchPlan createBatchPlan(List<Batch> batches) {
    BatchPlan plan = mock(BatchPlan.class);
    when(plan.batches()).thenReturn(batches);
    when(plan.totalBatches()).thenReturn(batches.size());
    when(plan.hasBatches()).thenReturn(!batches.isEmpty());
    when(plan.exceedsLimit()).thenReturn(false);
    return plan;
  }
}
