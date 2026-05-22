package dev.linqibin.patra.ingest.domain.model.aggregate;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.domain.model.enums.TaskStatus;
import dev.linqibin.patra.ingest.domain.model.vo.execution.ExecutionTimeline;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlanId;
import dev.linqibin.patra.ingest.domain.model.vo.plan.TaskSchedulerContext;
import dev.linqibin.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import dev.linqibin.patra.ingest.domain.model.vo.shared.LeaseInfo;
import dev.linqibin.patra.ingest.domain.model.vo.slice.PlanSliceId;
import dev.linqibin.patra.ingest.domain.model.vo.task.TaskId;
import java.time.Instant;

/// TaskAggregate 测试数据构建器。
///
/// 使用 Builder 模式简化测试用例中的聚合根构建。
///
/// ### 使用示例
///
/// ```java
/// // 创建队列中的任务
/// TaskAggregate task = TaskAggregateFixture.aQueuedTask().build();
///
/// // 创建运行中的任务，自定义租约信息
/// TaskAggregate task = TaskAggregateFixture.aRunningTask()
///     .leaseOwner("worker-1")
///     .leasedUntil(Instant.now().plusSeconds(300))
///     .build();
///
/// // 创建完全自定义的任务
/// TaskAggregate task = TaskAggregateFixture.aQueuedTask()
///     .id(1001L)
///     .provenanceCode("pubmed")
///     .operationCode("harvest")
///     .priority(10)
///     .retryCount(2)
///     .build();
/// ```
///
/// @author linqibin
/// @since 0.1.0
public class TaskAggregateFixture {

  // ========== 基础字段 ==========
  private TaskId id;
  private ScheduleInstanceId scheduleInstanceId = ScheduleInstanceId.of(1001L);
  private PlanId planId = PlanId.of(2001L);
  private PlanSliceId sliceId = PlanSliceId.of(3001L);
  private ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
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

  private TaskAggregateFixture() {}

  // ========== 静态工厂方法 ==========

  /// 创建默认的队列中任务构建器。
  ///
  /// @return 任务构建器
  public static TaskAggregateFixture aQueuedTask() {
    return new TaskAggregateFixture().status(TaskStatus.QUEUED);
  }

  /// 创建默认的运行中任务构建器。
  ///
  /// 包含以下默认配置：
  ///
  /// - 状态: RUNNING
  ///   - 租约持有者: worker-1
  ///   - 租约过期时间: 当前时间 + 5 分钟
  ///   - 租约计数: 1
  ///   - 开始时间: 2025-01-01T10:00:00Z
  ///
  /// @return 任务构建器
  public static TaskAggregateFixture aRunningTask() {
    return new TaskAggregateFixture()
        .status(TaskStatus.RUNNING)
        .leaseOwner("worker-1")
        .leasedUntil(Instant.now().plusSeconds(300))
        .leaseCount(1)
        .startedAt(Instant.parse("2025-01-01T10:00:00Z"));
  }

  /// 创建默认的成功任务构建器。
  ///
  /// 包含以下默认配置：
  ///
  /// - 状态: SUCCEEDED
  ///   - 开始时间: 2025-01-01T10:00:00Z
  ///   - 完成时间: 2025-01-01T10:05:00Z
  ///
  /// @return 任务构建器
  public static TaskAggregateFixture aSucceededTask() {
    return new TaskAggregateFixture()
        .status(TaskStatus.SUCCEEDED)
        .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
        .finishedAt(Instant.parse("2025-01-01T10:05:00Z"));
  }

  /// 创建默认的失败任务构建器。
  ///
  /// 包含以下默认配置：
  ///
  /// - 状态: FAILED
  ///   - 开始时间: 2025-01-01T10:00:00Z
  ///   - 完成时间: 2025-01-01T10:03:00Z
  ///   - 错误代码: ERR_GENERIC
  ///   - 错误消息: Test error
  ///
  /// @return 任务构建器
  public static TaskAggregateFixture aFailedTask() {
    return new TaskAggregateFixture()
        .status(TaskStatus.FAILED)
        .startedAt(Instant.parse("2025-01-01T10:00:00Z"))
        .finishedAt(Instant.parse("2025-01-01T10:03:00Z"))
        .lastErrorCode("ERR_GENERIC")
        .lastErrorMsg("Test error");
  }

  // ========== Builder 方法 ==========

  public TaskAggregateFixture id(TaskId id) {
    this.id = id;
    return this;
  }

  public TaskAggregateFixture scheduleInstanceId(ScheduleInstanceId scheduleInstanceId) {
    this.scheduleInstanceId = scheduleInstanceId;
    return this;
  }

  public TaskAggregateFixture scheduleInstanceId(Long scheduleInstanceId) {
    this.scheduleInstanceId =
        scheduleInstanceId != null ? ScheduleInstanceId.of(scheduleInstanceId) : null;
    return this;
  }

  public TaskAggregateFixture planId(PlanId planId) {
    this.planId = planId;
    return this;
  }

  public TaskAggregateFixture planId(Long planId) {
    this.planId = planId != null ? PlanId.of(planId) : null;
    return this;
  }

  public TaskAggregateFixture sliceId(PlanSliceId sliceId) {
    this.sliceId = sliceId;
    return this;
  }

  public TaskAggregateFixture sliceId(Long sliceId) {
    this.sliceId = sliceId != null ? PlanSliceId.of(sliceId) : null;
    return this;
  }

  public TaskAggregateFixture provenanceCode(ProvenanceCode provenanceCode) {
    this.provenanceCode = provenanceCode;
    return this;
  }

  public TaskAggregateFixture provenanceCode(String provenanceCode) {
    this.provenanceCode = ProvenanceCode.parse(provenanceCode);
    return this;
  }

  public TaskAggregateFixture operationCode(String operationCode) {
    this.operationCode = operationCode;
    return this;
  }

  public TaskAggregateFixture paramsJson(String paramsJson) {
    this.paramsJson = paramsJson;
    return this;
  }

  public TaskAggregateFixture idempotentKey(String idempotentKey) {
    this.idempotentKey = idempotentKey;
    return this;
  }

  public TaskAggregateFixture exprHash(String exprHash) {
    this.exprHash = exprHash;
    return this;
  }

  public TaskAggregateFixture priority(Integer priority) {
    this.priority = priority;
    return this;
  }

  public TaskAggregateFixture scheduledAt(Instant scheduledAt) {
    this.scheduledAt = scheduledAt;
    return this;
  }

  public TaskAggregateFixture lastHeartbeatAt(Instant lastHeartbeatAt) {
    this.lastHeartbeatAt = lastHeartbeatAt;
    return this;
  }

  public TaskAggregateFixture retryCount(Integer retryCount) {
    this.retryCount = retryCount;
    return this;
  }

  public TaskAggregateFixture lastErrorCode(String lastErrorCode) {
    this.lastErrorCode = lastErrorCode;
    return this;
  }

  public TaskAggregateFixture lastErrorMsg(String lastErrorMsg) {
    this.lastErrorMsg = lastErrorMsg;
    return this;
  }

  public TaskAggregateFixture status(TaskStatus status) {
    this.status = status;
    return this;
  }

  public TaskAggregateFixture leaseOwner(String leaseOwner) {
    this.leaseOwner = leaseOwner;
    return this;
  }

  public TaskAggregateFixture leasedUntil(Instant leasedUntil) {
    this.leasedUntil = leasedUntil;
    return this;
  }

  public TaskAggregateFixture leaseCount(Integer leaseCount) {
    this.leaseCount = leaseCount;
    return this;
  }

  public TaskAggregateFixture startedAt(Instant startedAt) {
    this.startedAt = startedAt;
    return this;
  }

  public TaskAggregateFixture finishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
    return this;
  }

  public TaskAggregateFixture correlationId(String correlationId) {
    this.correlationId = correlationId;
    return this;
  }

  public TaskAggregateFixture version(Long version) {
    this.version = version;
    return this;
  }

  // ========== Build 方法 ==========

  /// 构建 TaskAggregate 实例。
  ///
  /// 总是使用 `restore` 方法构建，以便能够完全控制所有字段的状态。
  ///
  /// @return TaskAggregate 实例
  public TaskAggregate build() {
    // 构建值对象
    LeaseInfo leaseInfo = LeaseInfo.snapshotOf(leaseOwner, leasedUntil, leaseCount);

    ExecutionTimeline executionTimeline =
        (startedAt == null && finishedAt == null)
            ? ExecutionTimeline.empty()
            : new ExecutionTimeline(startedAt, finishedAt);

    TaskSchedulerContext schedulerContext =
        correlationId == null
            ? TaskSchedulerContext.empty()
            : new TaskSchedulerContext(correlationId);

    // 统一使用 restore 方法，可以完全控制所有状态
    return TaskAggregate.restore(
        id, // 可以为 null，表示未持久化
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
