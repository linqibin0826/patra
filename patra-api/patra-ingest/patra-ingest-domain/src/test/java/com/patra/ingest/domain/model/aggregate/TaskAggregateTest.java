package com.patra.ingest.domain.model.aggregate;

import static org.assertj.core.api.Assertions.*;

import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.enums.TaskStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TaskAggregate 单元测试。
 *
 * <p>测试范围:
 *
 * <ul>
 *   <li>✅ 租约机制（获取、续期、释放、冲突）
 *   <li>✅ 执行时间线管理
 *   <li>✅ 状态转换逻辑
 *   <li>✅ 重试准备流程
 *   <li>✅ 领域事件发布
 *   <li>✅ 聚合根创建和恢复
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("TaskAggregate 单元测试")
class TaskAggregateTest {

  // ========== 聚合根创建与恢复 ==========

  @Nested
  @DisplayName("聚合根创建")
  class AggregateCreationTests {

    @Test
    @DisplayName("创建新任务时应该初始化为 QUEUED 状态")
    void shouldInitializeAsQueuedWhenCreatingNewTask() {
      // Given: 任务创建参数
      Instant scheduledAt = Instant.parse("2025-01-01T10:00:00Z");

      // When: 创建新任务
      TaskAggregate task =
          TaskAggregate.create(
              1001L, // scheduleInstanceId
              2001L, // planId
              3001L, // sliceId
              "pubmed", // provenanceCode
              "harvest", // operationCode
              "{\"batchSize\":100}", // paramsJson
              "idempotent-key-001", // idempotentKey
              "expr-hash-001", // exprHash
              10, // priority
              scheduledAt);

      // Then: 验证初始状态
      assertThat(task.getId()).isNull(); // 未持久化
      assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);
      assertThat(task.getScheduleInstanceId()).isEqualTo(1001L);
      assertThat(task.getPlanId()).isEqualTo(2001L);
      assertThat(task.getSliceId()).isEqualTo(3001L);
      assertThat(task.getProvenanceCode()).isEqualTo("pubmed");
      assertThat(task.getOperationCode()).isEqualTo("harvest");
      assertThat(task.getParamsJson()).isEqualTo("{\"batchSize\":100}");
      assertThat(task.getIdempotentKey()).isEqualTo("idempotent-key-001");
      assertThat(task.getExprHash()).isEqualTo("expr-hash-001");
      assertThat(task.getPriority()).isEqualTo(10);
      assertThat(task.getScheduledAt()).isEqualTo(scheduledAt);
      assertThat(task.getRetryCount()).isEqualTo(0);

      // 验证租约信息为空
      assertThat(task.getLeaseInfo()).isNotNull();
      assertThat(task.getLeaseInfo().isHeld()).isFalse();

      // 验证执行时间线为空
      assertThat(task.getExecutionTimeline()).isNotNull();
      assertThat(task.getExecutionTimeline().hasStarted()).isFalse();
      assertThat(task.getExecutionTimeline().hasFinished()).isFalse();

      // 验证调度器上下文为空
      assertThat(task.getSchedulerContext()).isNotNull();
      assertThat(task.getSchedulerContext().correlationId()).isNull();
    }

    @Test
    @DisplayName("从持久化恢复任务时应该保留所有状态")
    void shouldRestoreAllStateWhenRestoringFromPersistence() {
      // Given: 持久化状态
      Instant scheduledAt = Instant.parse("2025-01-01T10:00:00Z");
      Instant startedAt = Instant.parse("2025-01-01T10:05:00Z");
      Instant leasedUntil = Instant.parse("2025-01-01T10:15:00Z");

      // When: 从持久化恢复
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .id(5001L)
              .scheduleInstanceId(1001L)
              .planId(2001L)
              .sliceId(3001L)
              .provenanceCode("pubmed")
              .operationCode("harvest")
              .paramsJson("{\"test\":true}")
              .idempotentKey("restored-key-001")
              .exprHash("restored-hash-001")
              .priority(20)
              .scheduledAt(scheduledAt)
              .lastHeartbeatAt(startedAt)
              .retryCount(2)
              .lastErrorCode("ERR_TIMEOUT")
              .lastErrorMsg("Connection timeout")
              .status(TaskStatus.RUNNING)
              .leaseOwner("worker-1")
              .leasedUntil(leasedUntil)
              .leaseCount(3)
              .startedAt(startedAt)
              .version(10L)
              .build();

      // Then: 验证所有状态都被正确恢复
      assertThat(task.getId()).isEqualTo(5001L);
      assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
      assertThat(task.getScheduleInstanceId()).isEqualTo(1001L);
      assertThat(task.getPlanId()).isEqualTo(2001L);
      assertThat(task.getSliceId()).isEqualTo(3001L);
      assertThat(task.getProvenanceCode()).isEqualTo("pubmed");
      assertThat(task.getOperationCode()).isEqualTo("harvest");
      assertThat(task.getParamsJson()).isEqualTo("{\"test\":true}");
      assertThat(task.getIdempotentKey()).isEqualTo("restored-key-001");
      assertThat(task.getExprHash()).isEqualTo("restored-hash-001");
      assertThat(task.getPriority()).isEqualTo(20);
      assertThat(task.getScheduledAt()).isEqualTo(scheduledAt);
      assertThat(task.getLastHeartbeatAt()).isEqualTo(startedAt);
      assertThat(task.getRetryCount()).isEqualTo(2);
      assertThat(task.getLastErrorCode()).isEqualTo("ERR_TIMEOUT");
      assertThat(task.getLastErrorMsg()).isEqualTo("Connection timeout");
      assertThat(task.getVersion()).isEqualTo(10L);

      // 验证租约信息
      assertThat(task.getLeaseInfo().isHeld()).isTrue();
      assertThat(task.getLeaseInfo().owner()).isEqualTo("worker-1");
      assertThat(task.getLeaseInfo().leasedUntil()).isEqualTo(leasedUntil);
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(3);

      // 验证执行时间线
      assertThat(task.getExecutionTimeline().hasStarted()).isTrue();
      assertThat(task.getExecutionTimeline().startedAt()).isEqualTo(startedAt);
      assertThat(task.getExecutionTimeline().hasFinished()).isFalse();
    }
  }

  // ========== 租约机制测试 ==========

  @Nested
  @DisplayName("租约获取")
  class LeaseAcquisitionTests {

    @Test
    @DisplayName("应该成功为无租约的任务获取租约")
    void shouldAcquireLeaseForUnleasedTask() {
      // Given: 无租约的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();
      assertThat(task.getLeaseInfo().isHeld()).isFalse();

      // When: 获取租约
      Instant leasedUntil = Instant.now().plusSeconds(300);
      task.acquireLease("worker-1", leasedUntil);

      // Then: 租约应该被成功获取
      assertThat(task.getLeaseInfo().isHeld()).isTrue();
      assertThat(task.getLeaseInfo().owner()).isEqualTo("worker-1");
      assertThat(task.getLeaseInfo().leasedUntil()).isEqualTo(leasedUntil);
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("获取租约时应该增加租约计数")
    void shouldIncrementLeaseCountWhenAcquiringLease() {
      // Given: 无租约的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();

      // When: 获取租约
      task.acquireLease("worker-1", Instant.now().plusSeconds(300));

      // Then: 租约计数应该从 0 增加到 1
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("当租约已被持有时尝试获取应该抛出异常")
    void shouldThrowExceptionWhenAcquiringAlreadyHeldLease() {
      // Given: 已持有租约的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .build();

      assertThat(task.getLeaseInfo().isHeld()).isTrue();

      // When & Then: 尝试再次获取应该失败
      assertThatThrownBy(() -> task.acquireLease("worker-2", Instant.now().plusSeconds(300)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already held");
    }

    @Test
    @DisplayName("获取租约时所有者为 null 应该抛出异常")
    void shouldThrowExceptionWhenAcquiringLeaseWithNullOwner() {
      // Given: 无租约的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();

      // When & Then: 使用 null 所有者获取租约应该失败
      assertThatThrownBy(() -> task.acquireLease(null, Instant.now().plusSeconds(300)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("owner must not be blank");
    }

    @Test
    @DisplayName("获取租约时所有者为空字符串应该抛出异常")
    void shouldThrowExceptionWhenAcquiringLeaseWithBlankOwner() {
      // Given: 无租约的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();

      // When & Then: 使用空字符串所有者获取租约应该失败
      assertThatThrownBy(() -> task.acquireLease("  ", Instant.now().plusSeconds(300)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("owner must not be blank");
    }

    @Test
    @DisplayName("获取租约时过期时间为 null 应该抛出异常")
    void shouldThrowExceptionWhenAcquiringLeaseWithNullExpiration() {
      // Given: 无租约的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();

      // When & Then: 使用 null 过期时间获取租约应该失败
      assertThatThrownBy(() -> task.acquireLease("worker-1", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expiration must not be null");
    }
  }

  @Nested
  @DisplayName("租约续期")
  class LeaseRenewalTests {

    @Test
    @DisplayName("应该成功为持有者续约")
    void shouldRenewLeaseForCurrentHolder() {
      // Given: 持有租约的任务
      Instant initialExpiry = Instant.parse("2025-01-01T10:10:00Z");
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(initialExpiry)
              .leaseCount(1)
              .build();

      // When: 续约
      Instant newExpiry = Instant.parse("2025-01-01T10:20:00Z");
      task.renewLease("worker-1", newExpiry);

      // Then: 租约应该被成功续期
      assertThat(task.getLeaseInfo().isHeld()).isTrue();
      assertThat(task.getLeaseInfo().owner()).isEqualTo("worker-1");
      assertThat(task.getLeaseInfo().leasedUntil()).isEqualTo(newExpiry);
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(2); // 计数应该增加
    }

    @Test
    @DisplayName("续约时应该增加租约计数")
    void shouldIncrementLeaseCountWhenRenewingLease() {
      // Given: 持有租约的任务，租约计数为 3
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .leaseCount(3)
              .build();

      // When: 续约
      task.renewLease("worker-1", Instant.now().plusSeconds(600));

      // Then: 租约计数应该增加到 4
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("当任务无租约时尝试续约应该抛出异常")
    void shouldThrowExceptionWhenRenewingUnheldLease() {
      // Given: 无租约的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();

      // When & Then: 尝试续约应该失败
      assertThatThrownBy(() -> task.renewLease("worker-1", Instant.now().plusSeconds(300)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no lease holder");
    }

    @Test
    @DisplayName("当租约持有者不匹配时尝试续约应该抛出异常")
    void shouldThrowExceptionWhenRenewingWithDifferentHolder() {
      // Given: worker-1 持有租约
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .build();

      // When & Then: worker-2 尝试续约应该失败
      assertThatThrownBy(() -> task.renewLease("worker-2", Instant.now().plusSeconds(600)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("holder mismatch");
    }

    @Test
    @DisplayName("续约时持有者为 null 应该抛出异常")
    void shouldThrowExceptionWhenRenewingWithNullHolder() {
      // Given: 持有租约的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .build();

      // When & Then: 使用 null 持有者续约应该失败
      assertThatThrownBy(() -> task.renewLease(null, Instant.now().plusSeconds(600)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("holder used for renewal must not be blank");
    }

    @Test
    @DisplayName("续约时过期时间为 null 应该抛出异常")
    void shouldThrowExceptionWhenRenewingWithNullExpiration() {
      // Given: 持有租约的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .build();

      // When & Then: 使用 null 过期时间续约应该失败
      assertThatThrownBy(() -> task.renewLease("worker-1", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expiration must not be null");
    }
  }

  @Nested
  @DisplayName("租约释放")
  class LeaseReleaseTests {

    @Test
    @DisplayName("应该成功释放持有的租约")
    void shouldReleaseHeldLease() {
      // Given: 持有租约的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .leaseCount(2)
              .build();

      assertThat(task.getLeaseInfo().isHeld()).isTrue();

      // When: 释放租约
      task.releaseLease();

      // Then: 租约应该被释放
      assertThat(task.getLeaseInfo().isHeld()).isFalse();
      assertThat(task.getLeaseInfo().owner()).isNull();
      assertThat(task.getLeaseInfo().leasedUntil()).isNull();
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(2); // 计数保持不变
    }

    @Test
    @DisplayName("释放租约时应该保留租约计数")
    void shouldPreserveLeaseCountWhenReleasingLease() {
      // Given: 持有租约的任务，租约计数为 5
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .leaseCount(5)
              .build();

      // When: 释放租约
      task.releaseLease();

      // Then: 租约计数应该保持为 5
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("释放未持有的租约应该是幂等的")
    void shouldBeIdempotentWhenReleasingUnheldLease() {
      // Given: 无租约的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();
      assertThat(task.getLeaseInfo().isHeld()).isFalse();

      // When: 释放租约
      task.releaseLease();

      // Then: 应该保持无租约状态（幂等）
      assertThat(task.getLeaseInfo().isHeld()).isFalse();
      assertThat(task.getLeaseInfo().owner()).isNull();
      assertThat(task.getLeaseInfo().leasedUntil()).isNull();
    }
  }

  @Nested
  @DisplayName("租约冲突场景")
  class LeaseConflictTests {

    @Test
    @DisplayName("不同工作者不能同时持有同一任务的租约")
    void shouldNotAllowMultipleWorkersToHoldLease() {
      // Given: worker-1 持有租约
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .build();

      // When & Then: worker-2 尝试获取租约应该失败
      assertThatThrownBy(() -> task.acquireLease("worker-2", Instant.now().plusSeconds(300)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already held");
    }

    @Test
    @DisplayName("租约释放后应该允许新的工作者获取")
    void shouldAllowNewWorkerToAcquireAfterRelease() {
      // Given: worker-1 持有租约然后释放
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .leaseCount(2)
              .build();

      task.releaseLease();
      assertThat(task.getLeaseInfo().isHeld()).isFalse();

      // When: worker-2 尝试获取租约
      task.acquireLease("worker-2", Instant.now().plusSeconds(300));

      // Then: 应该成功获取
      assertThat(task.getLeaseInfo().isHeld()).isTrue();
      assertThat(task.getLeaseInfo().owner()).isEqualTo("worker-2");
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(3); // 在之前的基础上增加
    }
  }

  // ========== 执行时间线测试 ==========

  @Nested
  @DisplayName("执行时间线管理")
  class ExecutionTimelineTests {

    @Test
    @DisplayName("标记为运行中时应该记录开始时间")
    void shouldRecordStartTimeWhenMarkingAsRunning() {
      // Given: 队列中的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();
      assertThat(task.getExecutionTimeline().hasStarted()).isFalse();

      // When: 标记为运行中
      Instant startedAt = Instant.parse("2025-01-01T10:05:00Z");
      task.markRunning(startedAt, "correlation-001");

      // Then: 应该记录开始时间
      assertThat(task.getExecutionTimeline().hasStarted()).isTrue();
      assertThat(task.getExecutionTimeline().startedAt()).isEqualTo(startedAt);
      assertThat(task.getExecutionTimeline().hasFinished()).isFalse();
      assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
      assertThat(task.getSchedulerContext().correlationId()).isEqualTo("correlation-001");
    }

    @Test
    @DisplayName("标记为成功时应该记录完成时间")
    void shouldRecordFinishTimeWhenMarkingAsSucceeded() {
      // Given: 运行中的任务
      Instant startedAt = Instant.parse("2025-01-01T10:05:00Z");
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask().startedAt(startedAt).build();

      // When: 标记为成功
      Instant finishedAt = Instant.parse("2025-01-01T10:10:00Z");
      task.markSucceeded(finishedAt);

      // Then: 应该记录完成时间
      assertThat(task.getExecutionTimeline().hasFinished()).isTrue();
      assertThat(task.getExecutionTimeline().finishedAt()).isEqualTo(finishedAt);
      assertThat(task.getExecutionTimeline().startedAt()).isEqualTo(startedAt);
      assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("标记为失败时应该记录完成时间")
    void shouldRecordFinishTimeWhenMarkingAsFailed() {
      // Given: 运行中的任务
      Instant startedAt = Instant.parse("2025-01-01T10:05:00Z");
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask().startedAt(startedAt).build();

      // When: 标记为失败
      Instant finishedAt = Instant.parse("2025-01-01T10:08:00Z");
      task.markFailed(finishedAt);

      // Then: 应该记录完成时间
      assertThat(task.getExecutionTimeline().hasFinished()).isTrue();
      assertThat(task.getExecutionTimeline().finishedAt()).isEqualTo(finishedAt);
      assertThat(task.getExecutionTimeline().startedAt()).isEqualTo(startedAt);
      assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    @DisplayName("完成时间应该在开始时间之后")
    void shouldEnsureFinishTimeIsAfterStartTime() {
      // Given: 运行中的任务
      Instant startedAt = Instant.parse("2025-01-01T10:05:00Z");
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask().startedAt(startedAt).build();

      // When & Then: 尝试使用早于开始时间的完成时间应该失败
      Instant invalidFinishedAt = Instant.parse("2025-01-01T10:00:00Z");
      assertThatThrownBy(() -> task.markSucceeded(invalidFinishedAt))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be earlier than start time");
    }
  }

  // ========== 状态转换测试 ==========

  @Nested
  @DisplayName("状态转换")
  class StatusTransitionTests {

    @Test
    @DisplayName("应该正确执行 QUEUED -> RUNNING 转换")
    void shouldTransitionFromQueuedToRunning() {
      // Given: QUEUED 状态的任务
      TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();
      assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);

      // When: 标记为运行中
      task.markRunning(Instant.now(), "correlation-001");

      // Then: 状态应该转换为 RUNNING
      assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    @DisplayName("应该正确执行 RUNNING -> SUCCEEDED 转换")
    void shouldTransitionFromRunningToSucceeded() {
      // Given: RUNNING 状态的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
              .build();

      assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);

      // When: 标记为成功
      task.markSucceeded(Instant.parse("2025-01-01T10:05:00Z"));

      // Then: 状态应该转换为 SUCCEEDED
      assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("应该正确执行 RUNNING -> FAILED 转换")
    void shouldTransitionFromRunningToFailed() {
      // Given: RUNNING 状态的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
              .build();

      assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);

      // When: 标记为失败
      task.markFailed(Instant.parse("2025-01-01T10:05:00Z"));

      // Then: 状态应该转换为 FAILED
      assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    @DisplayName("应该正确执行 FAILED -> QUEUED 转换（准备重试）")
    void shouldTransitionFromFailedToQueuedWhenPreparingForRetry() {
      // Given: FAILED 状态的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .status(TaskStatus.FAILED)
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
              .finishedAt(Instant.parse("2025-01-01T10:05:00Z"))
              .build();

      assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);

      // When: 准备重试
      task.prepareForRetry();

      // Then: 状态应该转换为 QUEUED
      assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);
    }
  }

  // ========== 重试机制测试 ==========

  @Nested
  @DisplayName("重试准备流程")
  class RetryPreparationTests {

    @Test
    @DisplayName("准备重试时应该释放租约")
    void shouldReleaseLeaseWhenPreparingForRetry() {
      // Given: 持有租约的失败任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .status(TaskStatus.FAILED)
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .build();

      assertThat(task.getLeaseInfo().isHeld()).isTrue();

      // When: 准备重试
      task.prepareForRetry();

      // Then: 租约应该被释放
      assertThat(task.getLeaseInfo().isHeld()).isFalse();
      assertThat(task.getLeaseInfo().owner()).isNull();
      assertThat(task.getLeaseInfo().leasedUntil()).isNull();
    }

    @Test
    @DisplayName("准备重试时应该清除执行时间线")
    void shouldClearExecutionTimelineWhenPreparingForRetry() {
      // Given: 有执行时间线的失败任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .status(TaskStatus.FAILED)
              .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
              .finishedAt(Instant.parse("2025-01-01T10:05:00Z"))
              .build();

      assertThat(task.getExecutionTimeline().hasStarted()).isTrue();
      assertThat(task.getExecutionTimeline().hasFinished()).isTrue();

      // When: 准备重试
      task.prepareForRetry();

      // Then: 执行时间线应该被清空
      assertThat(task.getExecutionTimeline().hasStarted()).isFalse();
      assertThat(task.getExecutionTimeline().hasFinished()).isFalse();
    }

    @Test
    @DisplayName("准备重试时应该清除调度器上下文")
    void shouldClearSchedulerContextWhenPreparingForRetry() {
      // Given: 有调度器上下文的失败任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .status(TaskStatus.FAILED)
              .correlationId("old-correlation-001")
              .build();

      assertThat(task.getSchedulerContext().correlationId()).isNotNull();

      // When: 准备重试
      task.prepareForRetry();

      // Then: 调度器上下文应该被清空
      assertThat(task.getSchedulerContext().correlationId()).isNull();
    }

    @Test
    @DisplayName("准备重试时应该将状态重置为 QUEUED")
    void shouldResetStatusToQueuedWhenPreparingForRetry() {
      // Given: FAILED 状态的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask().status(TaskStatus.FAILED).build();

      // When: 准备重试
      task.prepareForRetry();

      // Then: 状态应该被重置为 QUEUED
      assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    @DisplayName("准备重试应该清除所有运行时上下文")
    void shouldClearAllRuntimeContextWhenPreparingForRetry() {
      // Given: 完整的失败任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .status(TaskStatus.FAILED)
              .leaseOwner("worker-1")
              .leasedUntil(Instant.now().plusSeconds(300))
              .leaseCount(2)
              .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
              .finishedAt(Instant.parse("2025-01-01T10:05:00Z"))
              .correlationId("correlation-001")
              .build();

      // When: 准备重试
      task.prepareForRetry();

      // Then: 所有运行时上下文都应该被清除
      assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);
      assertThat(task.getLeaseInfo().isHeld()).isFalse();
      assertThat(task.getExecutionTimeline().hasStarted()).isFalse();
      assertThat(task.getSchedulerContext().correlationId()).isNull();

      // 但租约计数应该保留（用于审计）
      assertThat(task.getLeaseInfo().leaseCount()).isEqualTo(2);
    }
  }

  // ========== 领域事件测试 ==========

  @Nested
  @DisplayName("领域事件发布")
  class DomainEventTests {

    @Test
    @DisplayName("应该发布任务入队事件")
    void shouldPublishTaskQueuedEvent() {
      // Given: 新创建的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aQueuedTask()
              .id(1001L)
              .planId(2001L)
              .sliceId(3001L)
              .scheduleInstanceId(4001L)
              .provenanceCode("pubmed")
              .operationCode("harvest")
              .idempotentKey("idem-001")
              .paramsJson("{\"test\":true}")
              .priority(10)
              .scheduledAt(Instant.parse("2025-01-01T10:00:00Z"))
              .build();

      // When: 发布入队事件
      TaskQueuedEvent event = task.raiseQueuedEvent();

      // Then: 应该发布正确的事件
      assertThat(event).isNotNull();
      assertThat(event.taskId()).isEqualTo(1001L);
      assertThat(event.planId()).isEqualTo(2001L);
      assertThat(event.sliceId()).isEqualTo(3001L);
      assertThat(event.scheduleInstanceId()).isEqualTo(4001L);
      assertThat(event.provenanceCode()).isEqualTo("pubmed");
      assertThat(event.operationCode()).isEqualTo("harvest");
      assertThat(event.idempotentKey()).isEqualTo("idem-001");
      assertThat(event.paramsJson()).isEqualTo("{\"test\":true}");
      assertThat(event.priority()).isEqualTo(10);
      assertThat(event.scheduledAt()).isEqualTo(Instant.parse("2025-01-01T10:00:00Z"));

      // 验证事件被添加到聚合根
      assertThat(task.getDomainEvents()).hasSize(1);
      assertThat(task.getDomainEvents().get(0)).isInstanceOf(TaskQueuedEvent.class);
    }

    @Test
    @DisplayName("应该在任务成功时发布完成事件")
    void shouldPublishCompletedEventWhenTaskSucceeds() {
      // Given: 运行中的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .id(1001L)
              .sliceId(3001L)
              .planId(2001L)
              .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
              .build();

      // When: 标记为成功
      Instant finishedAt = Instant.parse("2025-01-01T10:05:00Z");
      task.markSucceeded(finishedAt);

      // Then: 应该发布完成事件
      assertThat(task.getDomainEvents()).hasSize(1);
      TaskCompletedEvent event = (TaskCompletedEvent) task.getDomainEvents().get(0);
      assertThat(event.taskId()).isEqualTo(1001L);
      assertThat(event.sliceId()).isEqualTo(3001L);
      assertThat(event.planId()).isEqualTo(2001L);
      assertThat(event.statusCode()).isEqualTo(TaskStatus.SUCCEEDED.getCode());
      assertThat(event.completedAt()).isEqualTo(finishedAt);
      assertThat(event.errorCode()).isNull();
      assertThat(event.errorMsg()).isNull();
    }

    @Test
    @DisplayName("应该在任务失败时发布完成事件并包含错误信息")
    void shouldPublishCompletedEventWithErrorInfoWhenTaskFails() {
      // Given: 运行中的任务，包含错误信息
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask()
              .id(1001L)
              .sliceId(3001L)
              .planId(2001L)
              .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
              .lastErrorCode("ERR_TIMEOUT")
              .lastErrorMsg("Connection timeout after 30s")
              .build();

      // When: 标记为失败
      Instant finishedAt = Instant.parse("2025-01-01T10:05:00Z");
      task.markFailed(finishedAt);

      // Then: 应该发布包含错误信息的完成事件
      assertThat(task.getDomainEvents()).hasSize(1);
      TaskCompletedEvent event = (TaskCompletedEvent) task.getDomainEvents().get(0);
      assertThat(event.taskId()).isEqualTo(1001L);
      assertThat(event.sliceId()).isEqualTo(3001L);
      assertThat(event.planId()).isEqualTo(2001L);
      assertThat(event.statusCode()).isEqualTo(TaskStatus.FAILED.getCode());
      assertThat(event.completedAt()).isEqualTo(finishedAt);
      assertThat(event.errorCode()).isEqualTo("ERR_TIMEOUT");
      assertThat(event.errorMsg()).isEqualTo("Connection timeout after 30s");
    }
  }

  // ========== 业务规则测试 ==========

  @Nested
  @DisplayName("业务规则")
  class BusinessRuleTests {

    @Test
    @DisplayName("应该支持绑定计划和切片")
    void shouldSupportBindingPlanAndSlice() {
      // Given: 创建的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aQueuedTask().planId(null).sliceId(null).build();

      assertThat(task.getPlanId()).isNull();
      assertThat(task.getSliceId()).isNull();

      // When: 绑定计划和切片
      task.bindPlanAndSlice(5001L, 6001L);

      // Then: 应该成功绑定
      assertThat(task.getPlanId()).isEqualTo(5001L);
      assertThat(task.getSliceId()).isEqualTo(6001L);
    }

    @Test
    @DisplayName("标记为队列中状态应该更新状态为 QUEUED")
    void shouldUpdateStatusToQueuedWhenMarkingAsQueued() {
      // Given: 任意状态的任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aRunningTask().status(TaskStatus.RUNNING).build();

      // When: 标记为队列中
      task.markQueued();

      // Then: 状态应该更新为 QUEUED
      assertThat(task.getStatus()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    @DisplayName("不可变字段在创建后应该保持不变")
    void shouldPreserveImmutableFieldsAfterCreation() {
      // Given: 创建任务
      TaskAggregate task =
          TaskAggregateTestDataBuilder.aQueuedTask()
              .provenanceCode("pubmed")
              .operationCode("harvest")
              .paramsJson("{\"original\":true}")
              .idempotentKey("idem-original")
              .exprHash("hash-original")
              .priority(15)
              .scheduledAt(Instant.parse("2025-01-01T10:00:00Z"))
              .build();

      // When: 执行各种状态转换操作
      task.markRunning(Instant.now(), "correlation-001");
      task.acquireLease("worker-1", Instant.now().plusSeconds(300));

      // Then: 不可变字段应该保持不变
      assertThat(task.getProvenanceCode()).isEqualTo("pubmed");
      assertThat(task.getOperationCode()).isEqualTo("harvest");
      assertThat(task.getParamsJson()).isEqualTo("{\"original\":true}");
      assertThat(task.getIdempotentKey()).isEqualTo("idem-original");
      assertThat(task.getExprHash()).isEqualTo("hash-original");
      assertThat(task.getPriority()).isEqualTo(15);
      assertThat(task.getScheduledAt()).isEqualTo(Instant.parse("2025-01-01T10:00:00Z"));
    }
  }
}
