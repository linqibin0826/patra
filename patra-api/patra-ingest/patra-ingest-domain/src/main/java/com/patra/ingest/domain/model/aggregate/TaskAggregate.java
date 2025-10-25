package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.LeaseInfo;
import com.patra.ingest.domain.model.vo.TaskSchedulerContext;
import java.time.Instant;

/**
 * Aggregate root for ingestion tasks. Encapsulates scheduling metadata, execution progress, lease
 * ownership, and retry bookkeeping required to orchestrate task lifecycles.
 *
 * <p>Supports idempotency key generation, execution state transitions, and compensation flows.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class TaskAggregate extends AggregateRoot<Long> {

  /** Scheduler instance identifier. */
  private Long scheduleInstanceId;

  /** Owning plan identifier. */
  private Long planId;

  /** Owning slice identifier. */
  private Long sliceId;

  /** Provenance/source code. */
  private final String provenanceCode;

  /** Operation code. */
  private final String operationCode;

  /** Task parameter payload in JSON. */
  private final String paramsJson;

  /** Idempotency key. */
  private final String idempotentKey;

  /** Expression hash. */
  private final String exprHash;

  /** Scheduling priority. */
  private final Integer priority;

  /** Scheduled execution time. */
  private final Instant scheduledAt;

  /** Timestamp of the last heartbeat. */
  private final Instant lastHeartbeatAt;

  /** Retry count. */
  private final Integer retryCount;

  /** Most recent error code. */
  private final String lastErrorCode;

  /** Most recent error message. */
  private final String lastErrorMsg;

  /** Current task status. */
  private TaskStatus status;

  /** Lease ownership metadata. */
  private LeaseInfo leaseInfo;

  /** Execution timeline markers. */
  private ExecutionTimeline executionTimeline;

  /** Scheduler context propagated with the task. */
  private TaskSchedulerContext schedulerContext;

  private TaskAggregate(
      Long id,
      Long scheduleInstanceId,
      Long planId,
      Long sliceId,
      String provenanceCode,
      String operationCode,
      String paramsJson,
      String idempotentKey,
      String exprHash,
      Integer priority,
      Instant scheduledAt,
      Instant lastHeartbeatAt,
      Integer retryCount,
      String lastErrorCode,
      String lastErrorMsg,
      TaskStatus status,
      LeaseInfo leaseInfo,
      ExecutionTimeline executionTimeline,
      TaskSchedulerContext schedulerContext) {
    super(id);
    this.scheduleInstanceId = scheduleInstanceId;
    this.planId = planId;
    this.sliceId = sliceId;
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.paramsJson = paramsJson;
    this.idempotentKey = idempotentKey;
    this.exprHash = exprHash;
    this.priority = priority;
    this.scheduledAt = scheduledAt;
    this.lastHeartbeatAt = lastHeartbeatAt;
    this.retryCount = retryCount;
    this.lastErrorCode = lastErrorCode;
    this.lastErrorMsg = lastErrorMsg;
    this.status = status == null ? TaskStatus.QUEUED : status;
    this.leaseInfo = leaseInfo == null ? LeaseInfo.none() : leaseInfo;
    this.executionTimeline =
        executionTimeline == null ? ExecutionTimeline.empty() : executionTimeline;
    this.schedulerContext =
        schedulerContext == null ? TaskSchedulerContext.empty() : schedulerContext;
  }

  /**
   * Create a new task aggregate in the queued state.
   *
   * @param scheduleInstanceId scheduler instance identifier
   * @param planId owning plan identifier
   * @param sliceId owning slice identifier placeholder
   * @param provenanceCode provenance/source code
   * @param operationCode operation code
   * @param paramsJson task parameter payload in JSON
   * @param idempotentKey idempotency key
   * @param exprHash expression hash
   * @param priority scheduling priority
   * @param scheduledAt scheduled execution time
   * @return new task aggregate ready for persistence
   */
  public static TaskAggregate create(
      Long scheduleInstanceId,
      Long planId,
      Long sliceId,
      String provenanceCode,
      String operationCode,
      String paramsJson,
      String idempotentKey,
      String exprHash,
      Integer priority,
      Instant scheduledAt) {
    return new TaskAggregate(
        null,
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
        null,
        0,
        null,
        null,
        TaskStatus.QUEUED,
        LeaseInfo.none(),
        ExecutionTimeline.empty(),
        TaskSchedulerContext.empty());
  }

  /**
   * Rebuilds an existing task aggregate from persisted state.
   *
   * @param id task identifier
   * @param scheduleInstanceId scheduler instance identifier
   * @param planId owning plan identifier
   * @param sliceId owning slice identifier
   * @param provenanceCode provenance/source code
   * @param operationCode operation code
   * @param paramsJson task parameter payload in JSON
   * @param idempotentKey idempotency key
   * @param exprHash expression hash
   * @param priority scheduling priority
   * @param scheduledAt scheduled execution time
   * @param lastHeartbeatAt timestamp of last heartbeat
   * @param retryCount retry count
   * @param lastErrorCode most recent error code
   * @param lastErrorMsg most recent error message
   * @param status current task status
   * @param leaseInfo lease ownership metadata
   * @param executionTimeline execution timeline markers
   * @param schedulerContext scheduler context
   * @param version optimistic locking version
   * @return restored task aggregate
   */
  public static TaskAggregate restore(
      Long id,
      Long scheduleInstanceId,
      Long planId,
      Long sliceId,
      String provenanceCode,
      String operationCode,
      String paramsJson,
      String idempotentKey,
      String exprHash,
      Integer priority,
      Instant scheduledAt,
      Instant lastHeartbeatAt,
      Integer retryCount,
      String lastErrorCode,
      String lastErrorMsg,
      TaskStatus status,
      LeaseInfo leaseInfo,
      ExecutionTimeline executionTimeline,
      TaskSchedulerContext schedulerContext,
      long version) {
    TaskAggregate aggregate =
        createRestoredInstance(
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
            schedulerContext);
    aggregate.assignVersion(version);
    return aggregate;
  }

  private static TaskAggregate createRestoredInstance(
      Long id,
      Long scheduleInstanceId,
      Long planId,
      Long sliceId,
      String provenanceCode,
      String operationCode,
      String paramsJson,
      String idempotentKey,
      String exprHash,
      Integer priority,
      Instant scheduledAt,
      Instant lastHeartbeatAt,
      Integer retryCount,
      String lastErrorCode,
      String lastErrorMsg,
      TaskStatus status,
      LeaseInfo leaseInfo,
      ExecutionTimeline executionTimeline,
      TaskSchedulerContext schedulerContext) {
    return new TaskAggregate(
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
        schedulerContext);
  }

  /**
   * Binds this task to a specific plan and slice after persistence.
   *
   * @param planId plan identifier
   * @param sliceId slice identifier
   */
  public void bindPlanAndSlice(Long planId, Long sliceId) {
    this.planId = planId;
    this.sliceId = sliceId;
  }

  /**
   * Emits a domain event when the task is ready to be queued.
   *
   * <p>Application layer should publish this event to the message queue.
   *
   * @return task queued event
   */
  public TaskQueuedEvent raiseQueuedEvent() {
    TaskQueuedEvent event =
        TaskQueuedEvent.of(
            getId(),
            this.planId,
            this.sliceId,
            this.scheduleInstanceId,
            this.provenanceCode,
            this.operationCode,
            this.idempotentKey,
            this.paramsJson,
            this.priority,
            this.scheduledAt);
    addDomainEvent(event);
    return event;
  }

  /** Marks the task as queued. */
  public void markQueued() {
    this.status = TaskStatus.QUEUED;
  }

  /**
   * Marks the task as running and records execution context.
   *
   * @param startedAt execution start time
   * @param schedulerRunId scheduler run identifier
   * @param correlationId correlation identifier for tracing
   */
  public void markRunning(Instant startedAt, String schedulerRunId, String correlationId) {
    this.executionTimeline = executionTimeline.onStart(startedAt);
    this.schedulerContext =
        schedulerContext.withSchedulerRun(schedulerRunId).withCorrelation(correlationId);
    this.status = TaskStatus.RUNNING;
  }

  /**
   * Marks the task as succeeded.
   *
   * @param finishedAt execution finish time
   */
  public void markSucceeded(Instant finishedAt) {
    this.executionTimeline = executionTimeline.onFinish(finishedAt);
    this.status = TaskStatus.SUCCEEDED;
  }

  /**
   * Marks the task as failed.
   *
   * @param finishedAt execution finish time
   */
  public void markFailed(Instant finishedAt) {
    this.executionTimeline = executionTimeline.onFinish(finishedAt);
    this.status = TaskStatus.FAILED;
  }

  /**
   * Marks the task as partially completed.
   *
   * @param finishedAt execution finish time
   */
  public void markPartial(Instant finishedAt) {
    this.executionTimeline = executionTimeline.onFinish(finishedAt);
    this.status = TaskStatus.PARTIAL;
  }

  /**
   * Marks the task as pending cursor advancement.
   *
   * @param finishedAt execution finish time
   */
  public void markCursorPending(Instant finishedAt) {
    this.executionTimeline = executionTimeline.onFinish(finishedAt);
    this.status = TaskStatus.CURSOR_PENDING;
  }

  /**
   * Marks the task as cancelled.
   *
   * @param finishedAt cancellation time
   */
  public void markCancelled(Instant finishedAt) {
    this.executionTimeline = executionTimeline.onFinish(finishedAt);
    this.status = TaskStatus.CANCELLED;
  }

  /**
   * Acquires a lease for this task.
   *
   * @param owner lease owner identifier
   * @param leasedUntil lease expiration time
   */
  public void acquireLease(String owner, Instant leasedUntil) {
    this.leaseInfo = leaseInfo.acquire(owner, leasedUntil);
  }

  /**
   * Renews the existing lease.
   *
   * @param owner lease owner identifier
   * @param leasedUntil new lease expiration time
   */
  public void renewLease(String owner, Instant leasedUntil) {
    this.leaseInfo = leaseInfo.renew(owner, leasedUntil);
  }

  /** Releases the current lease. */
  public void releaseLease() {
    this.leaseInfo = leaseInfo.release();
  }

  /**
   * Resets the task for retry by clearing runtime context.
   *
   * <p>Releases lease, clears execution timeline and scheduler context, then marks as queued.
   */
  public void prepareForRetry() {
    releaseLease();
    this.executionTimeline = ExecutionTimeline.empty();
    this.schedulerContext = TaskSchedulerContext.empty();
    markQueued();
  }

  public LeaseInfo getLeaseInfo() {
    return leaseInfo;
  }

  public ExecutionTimeline getExecutionTimeline() {
    return executionTimeline;
  }

  public TaskSchedulerContext getSchedulerContext() {
    return schedulerContext;
  }

  public Long getScheduleInstanceId() {
    return scheduleInstanceId;
  }

  public Long getPlanId() {
    return planId;
  }

  public Long getSliceId() {
    return sliceId;
  }

  public String getProvenanceCode() {
    return provenanceCode;
  }

  public String getOperationCode() {
    return operationCode;
  }

  public String getParamsJson() {
    return paramsJson;
  }

  public String getIdempotentKey() {
    return idempotentKey;
  }

  public String getExprHash() {
    return exprHash;
  }

  public Integer getPriority() {
    return priority;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public Instant getLastHeartbeatAt() {
    return lastHeartbeatAt;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public String getLastErrorMsg() {
    return lastErrorMsg;
  }
}
