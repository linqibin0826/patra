package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.BatchStats;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import java.time.Instant;
import lombok.Getter;

/** 单次运行的分页/令牌批次。 */
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

    public TaskRunBatch(Long id,
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
        this(id,
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
                null);
    }

    private TaskRunBatch(Long id,
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
                         String error) {
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
    }

    public static TaskRunBatch restore(Long id,
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
                                       String error) {
        return new TaskRunBatch(id,
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
                error);
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
     * 创建批次执行结果记录（简化工厂方法）。
     * <p>
     * 用于任务执行引擎在批次执行完成后创建记录。
     * </p>
     *
     * @param runId 运行ID
     * @param batchNo 批次序号
     * @param success 是否成功
     * @param fetchedCount 获取的记录数
     * @param nextCursorToken 下一个游标token
     * @param errorMessage 错误信息
     * @param storageKey 存储键
     * @return TaskRunBatch 实例
     */
    public static TaskRunBatch create(Long runId,
                                      int batchNo,
                                      boolean success,
                                      int fetchedCount,
                                      String nextCursorToken,
                                      String errorMessage,
                                      String storageKey) {
        BatchStatus status = success ? BatchStatus.SUCCEEDED : BatchStatus.FAILED;
        BatchStats stats = BatchStats.of(fetchedCount);
        Instant now = Instant.now();

        return new TaskRunBatch(
            null,  // id - 由数据库生成
            runId,
            null,  // taskId - 可选
            null,  // sliceId - 可选
            null,  // planId - 可选
            null,  // provenanceCode - 可选
            null,  // operationCode - 可选
            batchNo,
            null,  // pageNo - 可选
            null,  // pageSize - 可选
            null,  // beforeToken
            nextCursorToken,  // afterToken
            null,  // exprHash - 可选
            null,  // idempotentKey - 可选
            status,
            stats,
            now,   // committedAt
            errorMessage
        );
    }
}
