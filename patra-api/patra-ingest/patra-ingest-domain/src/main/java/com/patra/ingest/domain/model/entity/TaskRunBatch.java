package com.patra.ingest.domain.model.entity;

import com.patra.common.util.HashUtils;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchResult;
import com.patra.ingest.domain.model.vo.BatchStats;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import java.time.Instant;
import lombok.Getter;

/** Entity representing a pagination/token batch within a task run. */
@SuppressWarnings("unused")
@Getter
public class TaskRunBatch {
  private final Long id;
  private final Long runId;
  private final Long taskId;
  private final Long sliceId;
  private final Long planId;
  private final String provenanceCode;
  private final String operationCode;
  private final int batchNo;
  private final Integer pageNo;
  private final Integer pageSize;
  private final String beforeToken;
  private final String exprHash;
  private final IdempotentKey idempotentKey;
  private BatchStatus status;
  private BatchStats stats;
  private Instant committedAt;
  private String afterToken;
  private String error;
  private final String storageKey;

  public TaskRunBatch(
      Long id,
      Long runId,
      Long taskId,
      Long sliceId,
      Long planId,
      String provenanceCode,
      String operationCode,
      int batchNo,
      Integer pageNo,
      Integer pageSize,
      String beforeToken,
      String exprHash,
      IdempotentKey idempotentKey) {
    this(
        id,
        runId,
        taskId,
        sliceId,
        planId,
        provenanceCode,
        operationCode,
        batchNo,
        pageNo,
        pageSize,
        beforeToken,
        null,
        exprHash,
        idempotentKey,
        BatchStatus.RUNNING,
        BatchStats.of(0),
        null,
        null,
        null);
  }

  private TaskRunBatch(
      Long id,
      Long runId,
      Long taskId,
      Long sliceId,
      Long planId,
      String provenanceCode,
      String operationCode,
      int batchNo,
      Integer pageNo,
      Integer pageSize,
      String beforeToken,
      String afterToken,
      String exprHash,
      IdempotentKey idempotentKey,
      BatchStatus status,
      BatchStats stats,
      Instant committedAt,
      String error,
      String storageKey) {
    this.id = id;
    this.runId = runId;
    this.taskId = taskId;
    this.sliceId = sliceId;
    this.planId = planId;
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.batchNo = batchNo;
    this.pageNo = pageNo;
    this.pageSize = pageSize;
    this.beforeToken = beforeToken;
    this.afterToken = afterToken;
    this.exprHash = exprHash;
    this.idempotentKey = idempotentKey;
    this.status = status;
    this.stats = stats == null ? BatchStats.of(0) : stats;
    this.committedAt = committedAt;
    this.error = error;
    this.storageKey = storageKey;
  }

  public static TaskRunBatch restore(
      Long id,
      Long runId,
      Long taskId,
      Long sliceId,
      Long planId,
      String provenanceCode,
      String operationCode,
      int batchNo,
      Integer pageNo,
      Integer pageSize,
      String beforeToken,
      String afterToken,
      String exprHash,
      IdempotentKey idempotentKey,
      BatchStatus status,
      BatchStats stats,
      Instant committedAt,
      String error,
      String storageKey) {
    return new TaskRunBatch(
        id,
        runId,
        taskId,
        sliceId,
        planId,
        provenanceCode,
        operationCode,
        batchNo,
        pageNo,
        pageSize,
        beforeToken,
        afterToken,
        exprHash,
        idempotentKey,
        status,
        stats,
        committedAt,
        error,
        storageKey);
  }

  public void succeed(int count, String afterTok, Instant now) {
    this.stats = BatchStats.of(count);
    this.status = BatchStatus.SUCCEEDED;
    this.afterToken = afterTok;
    this.committedAt = now;
  }

  public void fail(String err, Instant now) {
    this.status = BatchStatus.FAILED;
    this.error = err;
    this.committedAt = now;
  }

  /**
   * Convenience factory for recording the result of a batch execution.
   *
   * <p>Used by the task execution engine after a batch completes.
   *
   * @param context execution context containing redundant identifiers
   * @param batch batch definition with cursor metadata
   * @param result execution result for the batch
   * @return {@link TaskRunBatch} instance
   */
  public static TaskRunBatch create(ExecutionContext context, Batch batch, BatchResult result) {
    BatchStatus status = result.success() ? BatchStatus.SUCCEEDED : BatchStatus.FAILED;
    BatchStats stats = BatchStats.of(result.fetchedCount());
    Instant now = Instant.now();

    // Generate idempotent key: prefer cursor token for idempotency; fallback to batch number.
    String beforeToken = batch.cursorToken();
    String idempotentSeed =
        context.runId()
            + "|"
            + (beforeToken != null && !beforeToken.isBlank()
                ? beforeToken
                : "batch:" + batch.batchNo());
    String idempotentMaterial = idempotentSeed;
    IdempotentKey idempotentKey = new IdempotentKey(HashUtils.sha256Hex(idempotentMaterial));

    return new TaskRunBatch(
        null, // id - generated by the database
        context.runId(),
        context.taskId(),
        context.sliceId(),
        context.planId(),
        context.provenanceCode(),
        context.operationCode(),
        batch.batchNo(),
        null, // pageNo - optional
        null, // pageSize - optional
        beforeToken,
        result.nextCursorToken(), // afterToken
        context.exprHash(),
        idempotentKey,
        status,
        stats,
        now, // committedAt
        result.errorMessage(),
        result.storageKey());
  }
}
