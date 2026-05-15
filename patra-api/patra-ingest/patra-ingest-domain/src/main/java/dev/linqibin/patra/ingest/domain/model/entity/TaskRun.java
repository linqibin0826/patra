package dev.linqibin.patra.ingest.domain.model.entity;

import dev.linqibin.patra.ingest.domain.model.enums.TaskRunStatus;
import dev.linqibin.patra.ingest.domain.model.vo.execution.RunContext;
import dev.linqibin.patra.ingest.domain.model.vo.execution.RunStats;
import dev.linqibin.patra.ingest.domain.model.vo.execution.TaskRunCheckpoint;
import dev.linqibin.patra.ingest.domain.model.vo.plan.WindowSpec;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import lombok.Getter;

/// 任务执行记录实体。表示单次任务执行尝试。
///
/// 标识：由 taskId + attemptNo 唯一标识。
///
/// 生命周期：
///
/// - 创建时处于 `PENDING` 状态
///   - 执行开始时转换为 `RUNNING` 状态
///   - 执行完成后转换为 `SUCCEEDED/FAILED/PARTIAL` 状态
///
/// 业务约束：
///
/// - 支持检查点机制，用于可恢复执行
///   - 部分成功状态 (PARTIAL) 携带检查点信息，支持断点续传
///   - 统计信息 (RunStats) 记录抓取、解析、保存的记录数
///
/// PARTIAL 状态携带检查点信息，支持断点续传。
@SuppressWarnings("unused")
@Getter
public class TaskRun {
  private final Long id;
  private final Long taskId;
  private final int attemptNo;
  private final ProvenanceCode provenanceCode;
  private final String operationCode;
  private TaskRunStatus status;
  private RunStats stats;
  private Instant startedAt;
  private Instant finishedAt;
  private Instant lastHeartbeat;
  private String error;
  private TaskRunCheckpoint checkpoint;
  private WindowSpec windowSpec;
  private RunContext runContext;

  public TaskRun(
      Long id, Long taskId, int attemptNo, ProvenanceCode provenanceCode, String operationCode) {
    this(
        id,
        taskId,
        attemptNo,
        provenanceCode,
        operationCode,
        TaskRunStatus.PENDING,
        RunStats.empty(),
        TaskRunCheckpoint.empty(),
        null,
        null,
        null,
        null,
        RunContext.empty(),
        null);
  }

  private TaskRun(
      Long id,
      Long taskId,
      int attemptNo,
      ProvenanceCode provenanceCode,
      String operationCode,
      TaskRunStatus status,
      RunStats stats,
      TaskRunCheckpoint checkpoint,
      WindowSpec windowSpec,
      Instant startedAt,
      Instant finishedAt,
      Instant lastHeartbeat,
      RunContext runContext,
      String error) {
    this.id = id;
    this.taskId = taskId;
    this.attemptNo = attemptNo;
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.status = status;
    this.stats = stats == null ? RunStats.empty() : stats;
    this.checkpoint = checkpoint == null ? TaskRunCheckpoint.empty() : checkpoint;
    this.windowSpec = windowSpec;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.lastHeartbeat = lastHeartbeat;
    this.runContext = runContext == null ? RunContext.empty() : runContext;
    this.error = error;
  }

  public static TaskRun restore(
      Long id,
      Long taskId,
      int attemptNo,
      ProvenanceCode provenanceCode,
      String operationCode,
      TaskRunStatus status,
      RunStats stats,
      Instant startedAt,
      Instant finishedAt,
      Instant lastHeartbeat,
      TaskRunCheckpoint checkpoint,
      WindowSpec windowSpec,
      RunContext runContext,
      String error) {
    return new TaskRun(
        id,
        taskId,
        attemptNo,
        provenanceCode,
        operationCode,
        status,
        stats,
        checkpoint,
        windowSpec,
        startedAt,
        finishedAt,
        lastHeartbeat,
        runContext,
        error);
  }

  public void start(Instant now) {
    if (status == TaskRunStatus.PENDING) {
      status = TaskRunStatus.RUNNING;
      startedAt = now;
    }
  }

  public void assignWindow(WindowSpec windowSpec) {
    this.windowSpec = windowSpec;
  }

  public void updateCheckpoint(TaskRunCheckpoint checkpoint) {
    this.checkpoint = checkpoint == null ? TaskRunCheckpoint.empty() : checkpoint;
  }

  public void heartbeat(Instant heartbeatAt) {
    this.lastHeartbeat = heartbeatAt;
  }

  public void bindRunContext(String correlationId) {
    this.runContext = this.runContext.withCorrelation(correlationId);
  }

  public void appendStats(RunStats delta) {
    stats = stats.add(delta);
  }

  public void succeed(Instant now) {
    status = TaskRunStatus.SUCCEEDED;
    finishedAt = now;
  }

  public void fail(String err, Instant now) {
    status = TaskRunStatus.FAILED;
    error = err;
    finishedAt = now;
  }

  public void markPartial(String err, Instant now) {
    status = TaskRunStatus.PARTIAL;
    error = err;
    finishedAt = now;
  }

  // Note: markCursorPending() method removed after refactoring
  // CURSOR_PENDING status merged into PARTIAL with checkpoint support for resumable execution
}
