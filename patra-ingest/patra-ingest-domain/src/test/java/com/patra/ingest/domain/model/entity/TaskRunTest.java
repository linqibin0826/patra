package com.patra.ingest.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.model.vo.execution.RunContext;
import com.patra.ingest.domain.model.vo.execution.RunStats;
import com.patra.ingest.domain.model.vo.execution.TaskRunCheckpoint;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// TaskRun 实体单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 使用 TestDataBuilder 模式构建测试数据
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
/// 
/// 测试范围：
/// 
/// - ✅ 构造函数测试（标准构造函数、restore 工厂方法）
///   - ✅ 状态转换测试（PENDING → RUNNING → SUCCEEDED/FAILED/PARTIAL）
///   - ✅ 心跳机制测试（heartbeat）
///   - ✅ 检查点机制测试（updateCheckpoint）
///   - ✅ 统计信息测试（appendStats）
///   - ✅ 窗口分配测试（assignWindow）
///   - ✅ 运行上下文测试（bindRunContext）
///   - ✅ 边界条件和异常情况
/// 
/// @author linqibin
/// @since 0.2.0
@DisplayName("TaskRun 实体单元测试")
class TaskRunTest {

  // ========== 构造函数测试 ==========

  @Nested
  @DisplayName("标准构造函数")
  class StandardConstructorTests {

    @Test
    @DisplayName("应该成功创建新 TaskRun 并初始化为 PENDING 状态")
    void shouldCreateNewTaskRunWithPendingStatus() {
      // Given: TaskRun 创建参数
      Long id = 1001L;
      Long taskId = 2001L;
      int attemptNo = 1;
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";

      // When: 创建新 TaskRun
      TaskRun taskRun = new TaskRun(id, taskId, attemptNo, provenanceCode, operationCode);

      // Then: 验证初始状态
      assertThat(taskRun.getId()).isEqualTo(id);
      assertThat(taskRun.getTaskId()).isEqualTo(taskId);
      assertThat(taskRun.getAttemptNo()).isEqualTo(attemptNo);
      assertThat(taskRun.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(taskRun.getOperationCode()).isEqualTo(operationCode);
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.PENDING);
      assertThat(taskRun.getStats()).isEqualTo(RunStats.empty());
      assertThat(taskRun.getCheckpoint()).isEqualTo(TaskRunCheckpoint.empty());
      assertThat(taskRun.getStartedAt()).isNull();
      assertThat(taskRun.getFinishedAt()).isNull();
      assertThat(taskRun.getLastHeartbeat()).isNull();
      assertThat(taskRun.getError()).isNull();
      assertThat(taskRun.getWindowSpec()).isNull();
      assertThat(taskRun.getRunContext()).isEqualTo(RunContext.empty());
    }

    @Test
    @DisplayName("应该正确初始化默认值对象")
    void shouldInitializeDefaultValueObjects() {
      // Given & When
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // Then: 验证默认值对象不为 null
      assertThat(taskRun.getStats()).isNotNull().isEqualTo(RunStats.empty());
      assertThat(taskRun.getCheckpoint()).isNotNull().isEqualTo(TaskRunCheckpoint.empty());
      assertThat(taskRun.getRunContext()).isNotNull().isEqualTo(RunContext.empty());
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreFactoryMethodTests {

    @Test
    @DisplayName("应该从持久化状态成功重建 TaskRun")
    void shouldRestoreTaskRunFromPersistentState() {
      // Given: 持久化状态
      Long id = 1001L;
      Long taskId = 2001L;
      int attemptNo = 2;
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";
      TaskRunStatus status = TaskRunStatus.RUNNING;
      RunStats stats = new RunStats(100, 95, 5, 10);
      Instant startedAt = Instant.parse("2025-01-01T10:00:00Z");
      Instant finishedAt = null;
      Instant lastHeartbeat = Instant.parse("2025-01-01T10:05:00Z");
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("{\"cursor\":\"token123\"}");
      WindowSpec windowSpec =
          WindowSpec.ofTime(
              Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-12-31T23:59:59Z"));
      RunContext runContext = new RunContext("correlation-001");
      String error = null;

      // When: 从持久化恢复
      TaskRun taskRun =
          TaskRun.restore(
              id,
              taskId,
              attemptNo,
              provenanceCode,
              operationCode,
              status,
              stats,
              startedAt,
              finishedAt,
              lastHeartbeat,
              checkpoint,
              windowSpec,
              runContext,
              error);

      // Then: 验证所有状态都被正确恢复
      assertThat(taskRun.getId()).isEqualTo(id);
      assertThat(taskRun.getTaskId()).isEqualTo(taskId);
      assertThat(taskRun.getAttemptNo()).isEqualTo(attemptNo);
      assertThat(taskRun.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(taskRun.getOperationCode()).isEqualTo(operationCode);
      assertThat(taskRun.getStatus()).isEqualTo(status);
      assertThat(taskRun.getStats()).isEqualTo(stats);
      assertThat(taskRun.getStartedAt()).isEqualTo(startedAt);
      assertThat(taskRun.getFinishedAt()).isEqualTo(finishedAt);
      assertThat(taskRun.getLastHeartbeat()).isEqualTo(lastHeartbeat);
      assertThat(taskRun.getCheckpoint()).isEqualTo(checkpoint);
      assertThat(taskRun.getWindowSpec()).isEqualTo(windowSpec);
      assertThat(taskRun.getRunContext()).isEqualTo(runContext);
      assertThat(taskRun.getError()).isEqualTo(error);
    }

    @Test
    @DisplayName("应该使用默认值当 stats 为 null")
    void shouldUseDefaultValueWhenStatsIsNull() {
      // Given: stats 为 null
      RunStats stats = null;

      // When
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().stats(stats).buildRestored();

      // Then: 应该使用空 stats
      assertThat(taskRun.getStats()).isNotNull().isEqualTo(RunStats.empty());
    }

    @Test
    @DisplayName("应该使用默认值当 checkpoint 为 null")
    void shouldUseDefaultValueWhenCheckpointIsNull() {
      // Given: checkpoint 为 null
      TaskRunCheckpoint checkpoint = null;

      // When
      TaskRun taskRun =
          TaskRunTestDataBuilder.aPendingTaskRun().checkpoint(checkpoint).buildRestored();

      // Then: 应该使用空 checkpoint
      assertThat(taskRun.getCheckpoint()).isNotNull().isEqualTo(TaskRunCheckpoint.empty());
    }

    @Test
    @DisplayName("应该使用默认值当 runContext 为 null")
    void shouldUseDefaultValueWhenRunContextIsNull() {
      // Given: runContext 为 null
      RunContext runContext = null;

      // When
      TaskRun taskRun =
          TaskRunTestDataBuilder.aPendingTaskRun().runContext(runContext).buildRestored();

      // Then: 应该使用空 runContext
      assertThat(taskRun.getRunContext()).isNotNull().isEqualTo(RunContext.empty());
    }
  }

  // ========== 状态转换测试 ==========

  @Nested
  @DisplayName("状态转换：start()")
  class StartTransitionTests {

    @Test
    @DisplayName("应该允许从 PENDING 转换到 RUNNING")
    void shouldAllowTransitionFromPendingToRunning() {
      // Given: PENDING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.PENDING);
      assertThat(taskRun.getStartedAt()).isNull();

      // When: 启动任务
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      taskRun.start(now);

      // Then: 状态应该转换为 RUNNING，并记录开始时间
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
      assertThat(taskRun.getStartedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("应该忽略从非 PENDING 状态调用 start()")
    void shouldIgnoreStartFromNonPendingStatus() {
      // Given: RUNNING 状态的 TaskRun
      Instant originalStartedAt = Instant.parse("2025-01-01T09:00:00Z");
      TaskRun taskRun =
          TaskRunTestDataBuilder.aRunningTaskRun().startedAt(originalStartedAt).buildRestored();

      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);

      // When: 尝试再次启动
      Instant newNow = Instant.parse("2025-01-01T10:00:00Z");
      taskRun.start(newNow);

      // Then: 状态和开始时间应该保持不变（幂等）
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
      assertThat(taskRun.getStartedAt()).isEqualTo(originalStartedAt);
    }

    @Test
    @DisplayName("应该忽略从 SUCCEEDED 状态调用 start()")
    void shouldIgnoreStartFromSucceededStatus() {
      // Given: SUCCEEDED 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aSucceededTaskRun().buildRestored();
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);

      // When: 尝试启动
      taskRun.start(Instant.now());

      // Then: 状态应该保持不变
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
    }
  }

  @Nested
  @DisplayName("状态转换：succeed()")
  class SucceedTransitionTests {

    @Test
    @DisplayName("应该正确执行 succeed() 并记录完成时间")
    void shouldSucceedAndRecordFinishTime() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
      assertThat(taskRun.getFinishedAt()).isNull();

      // When: 标记为成功
      Instant finishedAt = Instant.parse("2025-01-01T10:10:00Z");
      taskRun.succeed(finishedAt);

      // Then: 状态应该转换为 SUCCEEDED，并记录完成时间
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
      assertThat(taskRun.getFinishedAt()).isEqualTo(finishedAt);
      assertThat(taskRun.getError()).isNull();
    }

    @Test
    @DisplayName("应该允许从任意状态调用 succeed()")
    void shouldAllowSucceedFromAnyStatus() {
      // Given: PENDING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 标记为成功
      Instant now = Instant.now();
      taskRun.succeed(now);

      // Then: 应该成功转换
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
      assertThat(taskRun.getFinishedAt()).isEqualTo(now);
    }
  }

  @Nested
  @DisplayName("状态转换：fail()")
  class FailTransitionTests {

    @Test
    @DisplayName("应该正确执行 fail() 并记录错误信息")
    void shouldFailAndRecordErrorInfo() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
      assertThat(taskRun.getFinishedAt()).isNull();
      assertThat(taskRun.getError()).isNull();

      // When: 标记为失败
      String error = "Connection timeout after 30 seconds";
      Instant finishedAt = Instant.parse("2025-01-01T10:05:00Z");
      taskRun.fail(error, finishedAt);

      // Then: 状态应该转换为 FAILED，并记录错误信息和完成时间
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.FAILED);
      assertThat(taskRun.getError()).isEqualTo(error);
      assertThat(taskRun.getFinishedAt()).isEqualTo(finishedAt);
    }

    @Test
    @DisplayName("应该允许错误信息为 null")
    void shouldAllowNullErrorMessage() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();

      // When: 使用 null 错误信息标记为失败
      taskRun.fail(null, Instant.now());

      // Then: 应该成功转换
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.FAILED);
      assertThat(taskRun.getError()).isNull();
    }

    @Test
    @DisplayName("应该允许从任意状态调用 fail()")
    void shouldAllowFailFromAnyStatus() {
      // Given: PENDING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 标记为失败
      taskRun.fail("Early failure", Instant.now());

      // Then: 应该成功转换
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.FAILED);
      assertThat(taskRun.getError()).isEqualTo("Early failure");
    }
  }

  @Nested
  @DisplayName("状态转换：markPartial()")
  class MarkPartialTransitionTests {

    @Test
    @DisplayName("应该正确执行 markPartial() 并记录错误信息")
    void shouldMarkPartialAndRecordErrorInfo() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
      assertThat(taskRun.getFinishedAt()).isNull();
      assertThat(taskRun.getError()).isNull();

      // When: 标记为部分完成
      String error = "Partial failure: 3 out of 10 batches failed";
      Instant finishedAt = Instant.parse("2025-01-01T10:08:00Z");
      taskRun.markPartial(error, finishedAt);

      // Then: 状态应该转换为 PARTIAL，并记录错误信息和完成时间
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.PARTIAL);
      assertThat(taskRun.getError()).isEqualTo(error);
      assertThat(taskRun.getFinishedAt()).isEqualTo(finishedAt);
    }

    @Test
    @DisplayName("应该允许错误信息为 null")
    void shouldAllowNullErrorMessage() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();

      // When: 使用 null 错误信息标记为部分完成
      taskRun.markPartial(null, Instant.now());

      // Then: 应该成功转换
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.PARTIAL);
      assertThat(taskRun.getError()).isNull();
    }

    @Test
    @DisplayName("PARTIAL 状态应该保留检查点信息以支持恢复")
    void shouldPreserveCheckpointForResumableExecution() {
      // Given: RUNNING 状态的 TaskRun，包含检查点
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("{\"lastProcessedId\":1000}");
      TaskRun taskRun =
          TaskRunTestDataBuilder.aRunningTaskRun().checkpoint(checkpoint).buildRestored();

      // When: 标记为部分完成
      taskRun.markPartial("Partial completion", Instant.now());

      // Then: 检查点应该被保留
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.PARTIAL);
      assertThat(taskRun.getCheckpoint()).isEqualTo(checkpoint);
      assertThat(taskRun.getCheckpoint().isPresent()).isTrue();
    }
  }

  @Nested
  @DisplayName("完整状态转换流程")
  class FullStateTransitionFlowTests {

    @Test
    @DisplayName("应该成功完成 PENDING → RUNNING → SUCCEEDED 流程")
    void shouldCompleteFullSuccessFlow() {
      // Given: 新创建的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.PENDING);

      // When: 启动任务
      Instant startedAt = Instant.parse("2025-01-01T10:00:00Z");
      taskRun.start(startedAt);

      // Then: 应该处于 RUNNING 状态
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
      assertThat(taskRun.getStartedAt()).isEqualTo(startedAt);

      // When: 标记为成功
      Instant finishedAt = Instant.parse("2025-01-01T10:10:00Z");
      taskRun.succeed(finishedAt);

      // Then: 应该处于 SUCCEEDED 状态
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
      assertThat(taskRun.getFinishedAt()).isEqualTo(finishedAt);
    }

    @Test
    @DisplayName("应该成功完成 PENDING → RUNNING → FAILED 流程")
    void shouldCompleteFullFailureFlow() {
      // Given: 新创建的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 启动任务
      taskRun.start(Instant.parse("2025-01-01T10:00:00Z"));

      // Then: 应该处于 RUNNING 状态
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);

      // When: 标记为失败
      taskRun.fail("Database connection lost", Instant.parse("2025-01-01T10:03:00Z"));

      // Then: 应该处于 FAILED 状态
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.FAILED);
      assertThat(taskRun.getError()).isEqualTo("Database connection lost");
    }

    @Test
    @DisplayName("应该成功完成 PENDING → RUNNING → PARTIAL 流程")
    void shouldCompleteFullPartialFlow() {
      // Given: 新创建的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 启动任务
      taskRun.start(Instant.parse("2025-01-01T10:00:00Z"));

      // Then: 应该处于 RUNNING 状态
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.RUNNING);

      // When: 更新检查点并标记为部分完成
      taskRun.updateCheckpoint(new TaskRunCheckpoint("{\"batchIndex\":5}"));
      taskRun.markPartial("Stopped at batch 5", Instant.parse("2025-01-01T10:07:00Z"));

      // Then: 应该处于 PARTIAL 状态，并保留检查点
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.PARTIAL);
      assertThat(taskRun.getCheckpoint().isPresent()).isTrue();
    }
  }

  // ========== 心跳机制测试 ==========

  @Nested
  @DisplayName("心跳机制：heartbeat()")
  class HeartbeatTests {

    @Test
    @DisplayName("应该成功更新心跳时间")
    void shouldUpdateHeartbeatTime() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();
      assertThat(taskRun.getLastHeartbeat()).isNull();

      // When: 更新心跳
      Instant heartbeatAt = Instant.parse("2025-01-01T10:05:00Z");
      taskRun.heartbeat(heartbeatAt);

      // Then: 心跳时间应该被记录
      assertThat(taskRun.getLastHeartbeat()).isEqualTo(heartbeatAt);
    }

    @Test
    @DisplayName("应该允许多次更新心跳时间")
    void shouldAllowMultipleHeartbeatUpdates() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();

      // When: 第一次心跳
      Instant firstHeartbeat = Instant.parse("2025-01-01T10:01:00Z");
      taskRun.heartbeat(firstHeartbeat);

      // Then: 第一次心跳应该被记录
      assertThat(taskRun.getLastHeartbeat()).isEqualTo(firstHeartbeat);

      // When: 第二次心跳
      Instant secondHeartbeat = Instant.parse("2025-01-01T10:02:00Z");
      taskRun.heartbeat(secondHeartbeat);

      // Then: 心跳时间应该被更新
      assertThat(taskRun.getLastHeartbeat()).isEqualTo(secondHeartbeat);
    }

    @Test
    @DisplayName("应该允许从任意状态更新心跳")
    void shouldAllowHeartbeatFromAnyStatus() {
      // Given: PENDING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 更新心跳
      Instant heartbeatAt = Instant.now();
      taskRun.heartbeat(heartbeatAt);

      // Then: 心跳应该被记录
      assertThat(taskRun.getLastHeartbeat()).isEqualTo(heartbeatAt);
    }
  }

  // ========== 检查点机制测试 ==========

  @Nested
  @DisplayName("检查点机制：updateCheckpoint()")
  class CheckpointTests {

    @Test
    @DisplayName("应该成功更新检查点")
    void shouldUpdateCheckpoint() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();
      assertThat(taskRun.getCheckpoint()).isEqualTo(TaskRunCheckpoint.empty());

      // When: 更新检查点
      TaskRunCheckpoint checkpoint =
          new TaskRunCheckpoint("{\"cursor\":\"page-5\",\"offset\":500}");
      taskRun.updateCheckpoint(checkpoint);

      // Then: 检查点应该被更新
      assertThat(taskRun.getCheckpoint()).isEqualTo(checkpoint);
      assertThat(taskRun.getCheckpoint().isPresent()).isTrue();
    }

    @Test
    @DisplayName("应该将 null 检查点转换为空检查点")
    void shouldConvertNullCheckpointToEmpty() {
      // Given: RUNNING 状态的 TaskRun，已有检查点
      TaskRunCheckpoint existingCheckpoint = new TaskRunCheckpoint("{\"data\":\"old\"}");
      TaskRun taskRun =
          TaskRunTestDataBuilder.aRunningTaskRun().checkpoint(existingCheckpoint).buildRestored();

      assertThat(taskRun.getCheckpoint().isPresent()).isTrue();

      // When: 使用 null 更新检查点
      taskRun.updateCheckpoint(null);

      // Then: 检查点应该被转换为空检查点
      assertThat(taskRun.getCheckpoint()).isEqualTo(TaskRunCheckpoint.empty());
      assertThat(taskRun.getCheckpoint().isPresent()).isFalse();
    }

    @Test
    @DisplayName("应该允许多次更新检查点")
    void shouldAllowMultipleCheckpointUpdates() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();

      // When: 第一次更新检查点
      TaskRunCheckpoint checkpoint1 = new TaskRunCheckpoint("{\"page\":1}");
      taskRun.updateCheckpoint(checkpoint1);

      // Then: 检查点应该被更新
      assertThat(taskRun.getCheckpoint()).isEqualTo(checkpoint1);

      // When: 第二次更新检查点
      TaskRunCheckpoint checkpoint2 = new TaskRunCheckpoint("{\"page\":2}");
      taskRun.updateCheckpoint(checkpoint2);

      // Then: 检查点应该被更新为最新值
      assertThat(taskRun.getCheckpoint()).isEqualTo(checkpoint2);
    }

    @Test
    @DisplayName("应该允许从任意状态更新检查点")
    void shouldAllowCheckpointUpdateFromAnyStatus() {
      // Given: PENDING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 更新检查点
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("{\"init\":true}");
      taskRun.updateCheckpoint(checkpoint);

      // Then: 检查点应该被更新
      assertThat(taskRun.getCheckpoint()).isEqualTo(checkpoint);
    }
  }

  // ========== 统计信息测试 ==========

  @Nested
  @DisplayName("统计信息：appendStats()")
  class StatsTests {

    @Test
    @DisplayName("应该成功累加统计信息")
    void shouldAppendStats() {
      // Given: RUNNING 状态的 TaskRun，初始统计为空
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();
      assertThat(taskRun.getStats()).isEqualTo(RunStats.empty());

      // When: 追加统计信息
      RunStats delta = new RunStats(100, 95, 5, 10);
      taskRun.appendStats(delta);

      // Then: 统计信息应该被累加
      assertThat(taskRun.getStats()).isEqualTo(delta);
      assertThat(taskRun.getStats().fetched()).isEqualTo(100);
      assertThat(taskRun.getStats().upserted()).isEqualTo(95);
      assertThat(taskRun.getStats().failed()).isEqualTo(5);
      assertThat(taskRun.getStats().pages()).isEqualTo(10);
    }

    @Test
    @DisplayName("应该正确累加多次统计信息")
    void shouldAppendStatsMultipleTimes() {
      // Given: RUNNING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aRunningTaskRun().buildRestored();

      // When: 第一次追加统计
      RunStats delta1 = new RunStats(100, 95, 5, 10);
      taskRun.appendStats(delta1);

      // Then: 统计应该被记录
      assertThat(taskRun.getStats()).isEqualTo(delta1);

      // When: 第二次追加统计
      RunStats delta2 = new RunStats(50, 48, 2, 5);
      taskRun.appendStats(delta2);

      // Then: 统计应该被累加
      assertThat(taskRun.getStats().fetched()).isEqualTo(150); // 100 + 50
      assertThat(taskRun.getStats().upserted()).isEqualTo(143); // 95 + 48
      assertThat(taskRun.getStats().failed()).isEqualTo(7); // 5 + 2
      assertThat(taskRun.getStats().pages()).isEqualTo(15); // 10 + 5
    }

    @Test
    @DisplayName("应该正确处理零值增量")
    void shouldHandleZeroDelta() {
      // Given: RUNNING 状态的 TaskRun，已有统计
      RunStats initialStats = new RunStats(100, 95, 5, 10);
      TaskRun taskRun =
          TaskRunTestDataBuilder.aRunningTaskRun().stats(initialStats).buildRestored();

      // When: 追加零值增量
      RunStats zeroDelta = RunStats.empty();
      taskRun.appendStats(zeroDelta);

      // Then: 统计应该保持不变
      assertThat(taskRun.getStats()).isEqualTo(initialStats);
    }

    @Test
    @DisplayName("应该允许从任意状态追加统计")
    void shouldAllowAppendStatsFromAnyStatus() {
      // Given: PENDING 状态的 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 追加统计
      RunStats delta = new RunStats(10, 10, 0, 1);
      taskRun.appendStats(delta);

      // Then: 统计应该被记录
      assertThat(taskRun.getStats()).isEqualTo(delta);
    }
  }

  // ========== 窗口分配测试 ==========

  @Nested
  @DisplayName("窗口分配：assignWindow()")
  class AssignWindowTests {

    @Test
    @DisplayName("应该成功分配时间窗口")
    void shouldAssignTimeWindow() {
      // Given: TaskRun 无窗口
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();
      assertThat(taskRun.getWindowSpec()).isNull();

      // When: 分配时间窗口
      WindowSpec windowSpec =
          WindowSpec.ofTime(
              Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-31T23:59:59Z"));
      taskRun.assignWindow(windowSpec);

      // Then: 窗口应该被分配
      assertThat(taskRun.getWindowSpec()).isEqualTo(windowSpec);
    }

    @Test
    @DisplayName("应该成功分配 ID 范围窗口")
    void shouldAssignIdRangeWindow() {
      // Given: TaskRun 无窗口
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 分配 ID 范围窗口
      WindowSpec windowSpec = WindowSpec.ofIdRange(1000L, 2000L);
      taskRun.assignWindow(windowSpec);

      // Then: 窗口应该被分配
      assertThat(taskRun.getWindowSpec()).isEqualTo(windowSpec);
    }

    @Test
    @DisplayName("应该成功分配游标窗口")
    void shouldAssignCursorWindow() {
      // Given: TaskRun 无窗口
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();

      // When: 分配游标窗口
      WindowSpec windowSpec = WindowSpec.ofCursor("start-token", "end-token");
      taskRun.assignWindow(windowSpec);

      // Then: 窗口应该被分配
      assertThat(taskRun.getWindowSpec()).isEqualTo(windowSpec);
    }

    @Test
    @DisplayName("应该允许覆盖已有窗口")
    void shouldAllowOverwritingExistingWindow() {
      // Given: TaskRun 已有窗口
      WindowSpec oldWindow = WindowSpec.ofSingle();
      TaskRun taskRun =
          TaskRunTestDataBuilder.aPendingTaskRun()
              .windowSpec(oldWindow)
              .buildRestored(); // 使用 buildRestored() 以保留 windowSpec

      assertThat(taskRun.getWindowSpec()).isEqualTo(oldWindow);

      // When: 分配新窗口
      WindowSpec newWindow =
          WindowSpec.ofTime(
              Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-12-31T23:59:59Z"));
      taskRun.assignWindow(newWindow);

      // Then: 窗口应该被覆盖
      assertThat(taskRun.getWindowSpec()).isEqualTo(newWindow);
    }

    @Test
    @DisplayName("应该允许分配 null 窗口")
    void shouldAllowAssigningNullWindow() {
      // Given: TaskRun 已有窗口
      WindowSpec window = WindowSpec.ofSingle();
      TaskRun taskRun =
          TaskRunTestDataBuilder.aPendingTaskRun()
              .windowSpec(window)
              .buildRestored(); // 使用 buildRestored() 以保留 windowSpec

      // When: 分配 null 窗口
      taskRun.assignWindow(null);

      // Then: 窗口应该被清除
      assertThat(taskRun.getWindowSpec()).isNull();
    }
  }

  // ========== 运行上下文测试 ==========

  @Nested
  @DisplayName("运行上下文：bindRunContext()")
  class RunContextTests {

    @Test
    @DisplayName("应该成功绑定 correlationId")
    void shouldBindCorrelationId() {
      // Given: TaskRun 无 correlationId
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().build();
      assertThat(taskRun.getRunContext().correlationId()).isNull();

      // When: 绑定 correlationId
      String correlationId = "trace-001";
      taskRun.bindRunContext(correlationId);

      // Then: correlationId 应该被绑定
      assertThat(taskRun.getRunContext().correlationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("应该允许覆盖已有 correlationId")
    void shouldAllowOverwritingExistingCorrelationId() {
      // Given: TaskRun 已有 correlationId
      RunContext oldContext = new RunContext("trace-old");
      TaskRun taskRun =
          TaskRunTestDataBuilder.aPendingTaskRun().runContext(oldContext).buildRestored();

      assertThat(taskRun.getRunContext().correlationId()).isEqualTo("trace-old");

      // When: 绑定新 correlationId
      String newCorrelationId = "trace-new";
      taskRun.bindRunContext(newCorrelationId);

      // Then: correlationId 应该被覆盖
      assertThat(taskRun.getRunContext().correlationId()).isEqualTo(newCorrelationId);
    }

    @Test
    @DisplayName("应该允许绑定 null correlationId")
    void shouldAllowBindingNullCorrelationId() {
      // Given: TaskRun 已有 correlationId
      RunContext context = new RunContext("trace-001");
      TaskRun taskRun =
          TaskRunTestDataBuilder.aPendingTaskRun().runContext(context).buildRestored();

      // When: 绑定 null correlationId
      taskRun.bindRunContext(null);

      // Then: correlationId 应该被清除
      assertThat(taskRun.getRunContext().correlationId()).isNull();
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极端时间值")
    void shouldHandleExtremeTimeValues() {
      // Given: 极端时间值
      Instant minTime = Instant.parse("1970-01-01T00:00:00Z");
      Instant maxTime = Instant.parse("2099-12-31T23:59:59Z");

      // When: 创建 TaskRun
      TaskRun taskRun =
          TaskRun.restore(
              1001L,
              2001L,
              1,
              ProvenanceCode.PUBMED,
              "HARVEST",
              TaskRunStatus.SUCCEEDED,
              RunStats.empty(),
              minTime,
              maxTime,
              maxTime,
              TaskRunCheckpoint.empty(),
              null,
              RunContext.empty(),
              null);

      // Then: 应该正确处理
      assertThat(taskRun.getStartedAt()).isEqualTo(minTime);
      assertThat(taskRun.getFinishedAt()).isEqualTo(maxTime);
      assertThat(taskRun.getLastHeartbeat()).isEqualTo(maxTime);
    }

    @Test
    @DisplayName("应该处理极大统计值")
    void shouldHandleLargeStatisticsValues() {
      // Given: 极大统计值
      long largeValue = Long.MAX_VALUE / 2;
      RunStats largeStats = new RunStats(largeValue, largeValue, largeValue, largeValue);

      // When: 创建 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().stats(largeStats).buildRestored();

      // Then: 应该正确处理
      assertThat(taskRun.getStats()).isEqualTo(largeStats);
    }

    @Test
    @DisplayName("应该处理极长检查点数据")
    void shouldHandleVeryLongCheckpointData() {
      // Given: 极长检查点数据
      String longJson = "{\"data\":\"" + "x".repeat(10000) + "\"}";
      TaskRunCheckpoint longCheckpoint = new TaskRunCheckpoint(longJson);

      // When: 创建 TaskRun
      TaskRun taskRun =
          TaskRunTestDataBuilder.aPendingTaskRun().checkpoint(longCheckpoint).buildRestored();

      // Then: 应该正确处理
      assertThat(taskRun.getCheckpoint()).isEqualTo(longCheckpoint);
      assertThat(taskRun.getCheckpoint().raw()).hasSize(longJson.length());
    }

    @Test
    @DisplayName("应该处理极长错误信息")
    void shouldHandleVeryLongErrorMessage() {
      // Given: 极长错误信息
      String longError = "Error: " + "x".repeat(5000);

      // When: 创建 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aFailedTaskRun().error(longError).buildRestored();

      // Then: 应该正确处理
      assertThat(taskRun.getError()).isEqualTo(longError);
    }

    @Test
    @DisplayName("应该处理最小尝试次数")
    void shouldHandleMinimumAttemptNumber() {
      // Given: 尝试次数为 1
      int attemptNo = 1;

      // When: 创建 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().attemptNo(attemptNo).build();

      // Then: 应该正确处理
      assertThat(taskRun.getAttemptNo()).isEqualTo(attemptNo);
    }

    @Test
    @DisplayName("应该处理极大尝试次数")
    void shouldHandleLargeAttemptNumber() {
      // Given: 极大尝试次数
      int attemptNo = Integer.MAX_VALUE;

      // When: 创建 TaskRun
      TaskRun taskRun = TaskRunTestDataBuilder.aPendingTaskRun().attemptNo(attemptNo).build();

      // Then: 应该正确处理
      assertThat(taskRun.getAttemptNo()).isEqualTo(attemptNo);
    }
  }

  // ========== 业务规则测试 ==========

  @Nested
  @DisplayName("业务规则")
  class BusinessRuleTests {

    @Test
    @DisplayName("不可变字段在生命周期中应该保持不变")
    void shouldPreserveImmutableFieldsThroughLifecycle() {
      // Given: 新创建的 TaskRun
      Long id = 1001L;
      Long taskId = 2001L;
      int attemptNo = 1;
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";

      TaskRun taskRun = new TaskRun(id, taskId, attemptNo, provenanceCode, operationCode);

      // When: 执行各种操作
      taskRun.start(Instant.now());
      taskRun.heartbeat(Instant.now());
      taskRun.appendStats(new RunStats(10, 10, 0, 1));
      taskRun.updateCheckpoint(new TaskRunCheckpoint("{\"test\":true}"));
      taskRun.assignWindow(WindowSpec.ofSingle());
      taskRun.bindRunContext("correlation-001");
      taskRun.succeed(Instant.now());

      // Then: 不可变字段应该保持不变
      assertThat(taskRun.getId()).isEqualTo(id);
      assertThat(taskRun.getTaskId()).isEqualTo(taskId);
      assertThat(taskRun.getAttemptNo()).isEqualTo(attemptNo);
      assertThat(taskRun.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(taskRun.getOperationCode()).isEqualTo(operationCode);
    }

    @Test
    @DisplayName("每个 attemptNo 应该对应唯一的 TaskRun 实例")
    void shouldHaveUniqueTaskRunPerAttemptNumber() {
      // Given: 相同 taskId，不同 attemptNo
      Long taskId = 2001L;

      // When: 创建多个尝试
      TaskRun attempt1 = new TaskRun(1001L, taskId, 1, ProvenanceCode.PUBMED, "HARVEST");
      TaskRun attempt2 = new TaskRun(1002L, taskId, 2, ProvenanceCode.PUBMED, "HARVEST");
      TaskRun attempt3 = new TaskRun(1003L, taskId, 3, ProvenanceCode.PUBMED, "HARVEST");

      // Then: 应该是不同的实例
      assertThat(attempt1.getTaskId()).isEqualTo(taskId);
      assertThat(attempt2.getTaskId()).isEqualTo(taskId);
      assertThat(attempt3.getTaskId()).isEqualTo(taskId);
      assertThat(attempt1.getAttemptNo()).isEqualTo(1);
      assertThat(attempt2.getAttemptNo()).isEqualTo(2);
      assertThat(attempt3.getAttemptNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("PARTIAL 状态应该支持断点续传")
    void shouldSupportResumableExecutionInPartialStatus() {
      // Given: PARTIAL 状态的 TaskRun，包含检查点
      TaskRunCheckpoint checkpoint =
          new TaskRunCheckpoint("{\"lastBatchId\":50,\"processedCount\":5000}");
      TaskRun taskRun =
          TaskRunTestDataBuilder.aPartialTaskRun()
              .checkpoint(checkpoint)
              .stats(new RunStats(5000, 4950, 50, 50))
              .buildRestored();

      // Then: 应该处于 PARTIAL 状态，并保留检查点和统计信息
      assertThat(taskRun.getStatus()).isEqualTo(TaskRunStatus.PARTIAL);
      assertThat(taskRun.getCheckpoint()).isEqualTo(checkpoint);
      assertThat(taskRun.getCheckpoint().isPresent()).isTrue();
      assertThat(taskRun.getStats().fetched()).isEqualTo(5000);
      assertThat(taskRun.getStats().upserted()).isEqualTo(4950);
      assertThat(taskRun.getStats().failed()).isEqualTo(50);
    }

    @Test
    @DisplayName("应该支持可选字段为 null")
    void shouldSupportOptionalFieldsAsNull() {
      // Given: 可选字段为 null
      TaskRun taskRun =
          TaskRun.restore(
              1001L,
              2001L,
              1,
              null, // provenanceCode 可选
              null, // operationCode 可选
              TaskRunStatus.PENDING,
              null, // stats 可选（自动转为 empty）
              null, // startedAt 可选
              null, // finishedAt 可选
              null, // lastHeartbeat 可选
              null, // checkpoint 可选（自动转为 empty）
              null, // windowSpec 可选
              null, // runContext 可选（自动转为 empty）
              null // error 可选
              );

      // Then: 应该成功创建，并使用默认值
      assertThat(taskRun).isNotNull();
      assertThat(taskRun.getProvenanceCode()).isNull();
      assertThat(taskRun.getOperationCode()).isNull();
      assertThat(taskRun.getStats()).isEqualTo(RunStats.empty());
      assertThat(taskRun.getStartedAt()).isNull();
      assertThat(taskRun.getFinishedAt()).isNull();
      assertThat(taskRun.getLastHeartbeat()).isNull();
      assertThat(taskRun.getCheckpoint()).isEqualTo(TaskRunCheckpoint.empty());
      assertThat(taskRun.getWindowSpec()).isNull();
      assertThat(taskRun.getRunContext()).isEqualTo(RunContext.empty());
      assertThat(taskRun.getError()).isNull();
    }
  }

  // ========== TestDataBuilder (辅助类) ==========

  /// TaskRun 测试数据构建器。
/// 
/// 遵循 Builder 模式，提供默认值以简化测试数据构建。
  static class TaskRunTestDataBuilder {
    private Long id = 1001L;
    private Long taskId = 2001L;
    private int attemptNo = 1;
    private ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
    private String operationCode = "HARVEST";
    private TaskRunStatus status = TaskRunStatus.PENDING;
    private RunStats stats = RunStats.empty();
    private Instant startedAt = null;
    private Instant finishedAt = null;
    private Instant lastHeartbeat = null;
    private String error = null;
    private TaskRunCheckpoint checkpoint = TaskRunCheckpoint.empty();
    private WindowSpec windowSpec = null;
    private RunContext runContext = RunContext.empty();

    public static TaskRunTestDataBuilder aPendingTaskRun() {
      return new TaskRunTestDataBuilder().status(TaskRunStatus.PENDING);
    }

    public static TaskRunTestDataBuilder aRunningTaskRun() {
      return new TaskRunTestDataBuilder()
          .status(TaskRunStatus.RUNNING)
          .startedAt(Instant.parse("2025-01-01T10:00:00Z"));
    }

    public static TaskRunTestDataBuilder aSucceededTaskRun() {
      return new TaskRunTestDataBuilder()
          .status(TaskRunStatus.SUCCEEDED)
          .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
          .finishedAt(Instant.parse("2025-01-01T10:10:00Z"));
    }

    public static TaskRunTestDataBuilder aFailedTaskRun() {
      return new TaskRunTestDataBuilder()
          .status(TaskRunStatus.FAILED)
          .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
          .finishedAt(Instant.parse("2025-01-01T10:05:00Z"))
          .error("Test failure");
    }

    public static TaskRunTestDataBuilder aPartialTaskRun() {
      return new TaskRunTestDataBuilder()
          .status(TaskRunStatus.PARTIAL)
          .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
          .finishedAt(Instant.parse("2025-01-01T10:08:00Z"))
          .error("Partial completion")
          .checkpoint(new TaskRunCheckpoint("{\"lastProcessedId\":1000}"));
    }

    public TaskRunTestDataBuilder id(Long id) {
      this.id = id;
      return this;
    }

    public TaskRunTestDataBuilder taskId(Long taskId) {
      this.taskId = taskId;
      return this;
    }

    public TaskRunTestDataBuilder attemptNo(int attemptNo) {
      this.attemptNo = attemptNo;
      return this;
    }

    public TaskRunTestDataBuilder provenanceCode(ProvenanceCode provenanceCode) {
      this.provenanceCode = provenanceCode;
      return this;
    }

    public TaskRunTestDataBuilder operationCode(String operationCode) {
      this.operationCode = operationCode;
      return this;
    }

    public TaskRunTestDataBuilder status(TaskRunStatus status) {
      this.status = status;
      return this;
    }

    public TaskRunTestDataBuilder stats(RunStats stats) {
      this.stats = stats;
      return this;
    }

    public TaskRunTestDataBuilder startedAt(Instant startedAt) {
      this.startedAt = startedAt;
      return this;
    }

    public TaskRunTestDataBuilder finishedAt(Instant finishedAt) {
      this.finishedAt = finishedAt;
      return this;
    }

    public TaskRunTestDataBuilder lastHeartbeat(Instant lastHeartbeat) {
      this.lastHeartbeat = lastHeartbeat;
      return this;
    }

    public TaskRunTestDataBuilder error(String error) {
      this.error = error;
      return this;
    }

    public TaskRunTestDataBuilder checkpoint(TaskRunCheckpoint checkpoint) {
      this.checkpoint = checkpoint;
      return this;
    }

    public TaskRunTestDataBuilder windowSpec(WindowSpec windowSpec) {
      this.windowSpec = windowSpec;
      return this;
    }

    public TaskRunTestDataBuilder runContext(RunContext runContext) {
      this.runContext = runContext;
      return this;
    }

    /// 构建新创建的 TaskRun（使用标准构造函数）。
    public TaskRun build() {
      return new TaskRun(id, taskId, attemptNo, provenanceCode, operationCode);
    }

    /// 构建从持久化重建的 TaskRun（使用 restore() 工厂方法）。
    public TaskRun buildRestored() {
      return TaskRun.restore(
          id,
          taskId,
          attemptNo,
          provenanceCode,
          operationCode,
          status,
          stats,
          startedAt,
          finishedAt,
          lastHeartbeat,
          checkpoint,
          windowSpec,
          runContext,
          error);
    }
  }
}
