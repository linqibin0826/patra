package com.patra.ingest.domain.model.entity;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.util.HashUtils;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchResult;
import com.patra.ingest.domain.model.vo.batch.BatchStats;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.shared.IdempotentKey;
import java.time.Instant;
import lombok.Getter;

/**
 * 任务执行批次实体。表示任务执行中的单次分页/游标批次。
 *
 * <p>标识：由 runId + batchNo 唯一标识。
 *
 * <p>生命周期：
 *
 * <ul>
 *   <li>创建时处于 {@code RUNNING} 状态
 *   <li>执行完成后转换为 {@code SUCCEEDED/FAILED} 状态
 * </ul>
 *
 * <p>业务约束：
 *
 * <ul>
 *   <li>支持基于页码的分页 (pageNo + pageSize)
 *   <li>支持基于游标令牌的分页 (beforeToken → afterToken)
 *   <li>幂等键由 runId + (cursorToken or batchNo) 生成
 *   <li>批次统计 (BatchStats) 记录本批次抓取的记录数
 *   <li>可选存储键 (storageKey) 指向原始数据的对象存储位置
 * </ul>
 */
@SuppressWarnings("unused")
@Getter
public class TaskRunBatch {
  private final Long id;
  private final Long runId;
  private final Long taskId;
  private final Long sliceId;
  private final Long planId;
  private final ProvenanceCode provenanceCode;
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
      ProvenanceCode provenanceCode,
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
      ProvenanceCode provenanceCode,
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
      ProvenanceCode provenanceCode,
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
        batch.pageNo(), // pageNo - from Batch VO (page-based pagination)
        batch.pageSize(), // pageSize - from Batch VO
        beforeToken, // beforeToken - cursorToken for token-based, null for page-based
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
