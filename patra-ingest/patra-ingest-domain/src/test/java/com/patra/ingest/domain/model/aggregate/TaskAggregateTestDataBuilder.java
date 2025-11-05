package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.execution.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.plan.TaskSchedulerContext;
import com.patra.ingest.domain.model.vo.shared.LeaseInfo;
import java.time.Instant;

/**
 * TaskAggregate 测试数据构建器。
 *
 * <p>使用 Builder 模式简化测试用例中的聚合根构建。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 创建队列中的任务
 * TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask().build();
 *
 * // 创建运行中的任务，自定义租约信息
 * TaskAggregate task = TaskAggregateTestDataBuilder.aRunningTask()
 *     .leaseOwner("worker-1")
 *     .leasedUntil(Instant.now().plusSeconds(300))
 *     .build();
 *
 * // 创建完全自定义的任务
 * TaskAggregate task = TaskAggregateTestDataBuilder.aQueuedTask()
 *     .id(1001L)
 *     .provenanceCode("pubmed")
 *     .operationCode("harvest")
 *     .priority(10)
 *     .retryCount(2)
 *     .build();
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class TaskAggregateTestDataBuilder {

  // ========== 基础字段 ==========
  private Long id;
  private Long scheduleInstanceId = 1001L;
  private Long planId = 2001L;
  private Long sliceId = 3001L;
  private String provenanceCode = "pubmed";
  private String operationCode = "harvest";
  private String paramsJson = "{\"batchSize\":100}";
  private String idempotentKey = "test-idempotent-key";
  private String exprHash = "test-expr-hash";
  private Integer priority = 10;
  private Instant scheduledAt = Instant.parse("2025-01-01T10:00:00Z");
  private Instant lastHeartbeatAt;
  private Integer retryCount = 0;
  private String lastErrorCode;
  private String lastErrorMsg;
  private TaskStatus status = TaskStatus.QUEUED;

  // ========== 租约信息字段 ==========
  private String leaseOwner;
  private Instant leasedUntil;
  private Integer leaseCount = 0;

  // ========== 执行时间线字段 ==========
  private Instant startedAt;
  private Instant finishedAt;

  // ========== 调度器上下文字段 ==========
  private String correlationId;

  // ========== 版本字段 ==========
  private Long version = 0L;

  // ========== 构造函数（私有） ==========

  private TaskAggregateTestDataBuilder() {}

  // ========== 静态工厂方法 ==========

  /**
   * 创建默认的队列中任务构建器。
   *
   * @return 任务构建器
   */
  public static TaskAggregateTestDataBuilder aQueuedTask() {
    return new TaskAggregateTestDataBuilder().status(TaskStatus.QUEUED);
  }

  /**
   * 创建默认的运行中任务构建器。
   *
   * <p>包含以下默认配置：
   *
   * <ul>
   *   <li>状态: RUNNING
   *   <li>租约持有者: worker-1
   *   <li>租约过期时间: 当前时间 + 5 分钟
   *   <li>租约计数: 1
   *   <li>开始时间: 2025-01-01T10:00:00Z
   * </ul>
   *
   * @return 任务构建器
   */
  public static TaskAggregateTestDataBuilder aRunningTask() {
    return new TaskAggregateTestDataBuilder()
        .status(TaskStatus.RUNNING)
        .leaseOwner("worker-1")
        .leasedUntil(Instant.now().plusSeconds(300))
        .leaseCount(1)
        .startedAt(Instant.parse("2025-01-01T10:00:00Z"));
  }

  /**
   * 创建默认的成功任务构建器。
   *
   * <p>包含以下默认配置：
   *
   * <ul>
   *   <li>状态: SUCCEEDED
   *   <li>开始时间: 2025-01-01T10:00:00Z
   *   <li>完成时间: 2025-01-01T10:05:00Z
   * </ul>
   *
   * @return 任务构建器
   */
  public static TaskAggregateTestDataBuilder aSucceededTask() {
    return new TaskAggregateTestDataBuilder()
        .status(TaskStatus.SUCCEEDED)
        .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
        .finishedAt(Instant.parse("2025-01-01T10:05:00Z"));
  }

  /**
   * 创建默认的失败任务构建器。
   *
   * <p>包含以下默认配置：
   *
   * <ul>
   *   <li>状态: FAILED
   *   <li>开始时间: 2025-01-01T10:00:00Z
   *   <li>完成时间: 2025-01-01T10:03:00Z
   *   <li>错误代码: ERR_GENERIC
   *   <li>错误消息: Test error
   * </ul>
   *
   * @return 任务构建器
   */
  public static TaskAggregateTestDataBuilder aFailedTask() {
    return new TaskAggregateTestDataBuilder()
        .status(TaskStatus.FAILED)
        .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
        .finishedAt(Instant.parse("2025-01-01T10:03:00Z"))
        .lastErrorCode("ERR_GENERIC")
        .lastErrorMsg("Test error");
  }

  // ========== Builder 方法 ==========

  public TaskAggregateTestDataBuilder id(Long id) {
    this.id = id;
    return this;
  }

  public TaskAggregateTestDataBuilder scheduleInstanceId(Long scheduleInstanceId) {
    this.scheduleInstanceId = scheduleInstanceId;
    return this;
  }

  public TaskAggregateTestDataBuilder planId(Long planId) {
    this.planId = planId;
    return this;
  }

  public TaskAggregateTestDataBuilder sliceId(Long sliceId) {
    this.sliceId = sliceId;
    return this;
  }

  public TaskAggregateTestDataBuilder provenanceCode(String provenanceCode) {
    this.provenanceCode = provenanceCode;
    return this;
  }

  public TaskAggregateTestDataBuilder operationCode(String operationCode) {
    this.operationCode = operationCode;
    return this;
  }

  public TaskAggregateTestDataBuilder paramsJson(String paramsJson) {
    this.paramsJson = paramsJson;
    return this;
  }

  public TaskAggregateTestDataBuilder idempotentKey(String idempotentKey) {
    this.idempotentKey = idempotentKey;
    return this;
  }

  public TaskAggregateTestDataBuilder exprHash(String exprHash) {
    this.exprHash = exprHash;
    return this;
  }

  public TaskAggregateTestDataBuilder priority(Integer priority) {
    this.priority = priority;
    return this;
  }

  public TaskAggregateTestDataBuilder scheduledAt(Instant scheduledAt) {
    this.scheduledAt = scheduledAt;
    return this;
  }

  public TaskAggregateTestDataBuilder lastHeartbeatAt(Instant lastHeartbeatAt) {
    this.lastHeartbeatAt = lastHeartbeatAt;
    return this;
  }

  public TaskAggregateTestDataBuilder retryCount(Integer retryCount) {
    this.retryCount = retryCount;
    return this;
  }

  public TaskAggregateTestDataBuilder lastErrorCode(String lastErrorCode) {
    this.lastErrorCode = lastErrorCode;
    return this;
  }

  public TaskAggregateTestDataBuilder lastErrorMsg(String lastErrorMsg) {
    this.lastErrorMsg = lastErrorMsg;
    return this;
  }

  public TaskAggregateTestDataBuilder status(TaskStatus status) {
    this.status = status;
    return this;
  }

  public TaskAggregateTestDataBuilder leaseOwner(String leaseOwner) {
    this.leaseOwner = leaseOwner;
    return this;
  }

  public TaskAggregateTestDataBuilder leasedUntil(Instant leasedUntil) {
    this.leasedUntil = leasedUntil;
    return this;
  }

  public TaskAggregateTestDataBuilder leaseCount(Integer leaseCount) {
    this.leaseCount = leaseCount;
    return this;
  }

  public TaskAggregateTestDataBuilder startedAt(Instant startedAt) {
    this.startedAt = startedAt;
    return this;
  }

  public TaskAggregateTestDataBuilder finishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
    return this;
  }

  public TaskAggregateTestDataBuilder correlationId(String correlationId) {
    this.correlationId = correlationId;
    return this;
  }

  public TaskAggregateTestDataBuilder version(Long version) {
    this.version = version;
    return this;
  }

  // ========== Build 方法 ==========

  /**
   * 构建 TaskAggregate 实例。
   *
   * <p>如果 id 为 null，则创建新任务（使用 {@code create}）；否则从持久化恢复（使用 {@code restore}）。
   *
   * @return TaskAggregate 实例
   */
  public TaskAggregate build() {
    if (id == null) {
      // 创建新任务
      return TaskAggregate.create(
          scheduleInstanceId,
          planId,
          sliceId,
          provenanceCode,
          operationCode,
          paramsJson,
          idempotentKey,
          exprHash,
          priority,
          scheduledAt);
    } else {
      // 从持久化恢复
      LeaseInfo leaseInfo = LeaseInfo.snapshotOf(leaseOwner, leasedUntil, leaseCount);

      ExecutionTimeline executionTimeline =
          (startedAt == null && finishedAt == null)
              ? ExecutionTimeline.empty()
              : new ExecutionTimeline(startedAt, finishedAt);

      TaskSchedulerContext schedulerContext =
          correlationId == null
              ? TaskSchedulerContext.empty()
              : new TaskSchedulerContext(correlationId);

      return TaskAggregate.restore(
          id,
          scheduleInstanceId,
          planId,
          sliceId,
          provenanceCode,
          operationCode,
          paramsJson,
          idempotentKey,
          exprHash,
          priority,
          scheduledAt,
          lastHeartbeatAt,
          retryCount,
          lastErrorCode,
          lastErrorMsg,
          status,
          leaseInfo,
          executionTimeline,
          schedulerContext,
          version);
    }
  }
}
