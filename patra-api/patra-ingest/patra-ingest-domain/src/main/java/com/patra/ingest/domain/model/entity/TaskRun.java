package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.model.vo.RunContext;
import com.patra.ingest.domain.model.vo.RunStats;
import com.patra.ingest.domain.model.vo.TaskRunCheckpoint;
import com.patra.ingest.domain.model.vo.WindowSpec;
import java.time.Instant;
import lombok.Getter;

/** Entity representing a single task run attempt. */
@SuppressWarnings("unused")
@Getter
public class TaskRun {
  private final Long id;
  private final Long taskId;
  private final int attemptNo;
  private final String provenanceCode;
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

  public TaskRun(Long id, Long taskId, int attemptNo, String provenanceCode, String operationCode) {
    this(
        id,
        taskId,
        attemptNo,
        provenanceCode,
        operationCode,
        TaskRunStatus.PLANNED,
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
      String provenanceCode,
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
      String provenanceCode,
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
    if (status == TaskRunStatus.PLANNED) {
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

  public void bindRunContext(String schedulerRunId, String correlationId) {
    RunContext updated = this.runContext.withSchedulerRun(schedulerRunId);
    updated = updated.withCorrelation(correlationId);
    this.runContext = updated;
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

  public void markCursorPending(Instant now) {
    status = TaskRunStatus.CURSOR_PENDING;
    finishedAt = now;
  }
}
