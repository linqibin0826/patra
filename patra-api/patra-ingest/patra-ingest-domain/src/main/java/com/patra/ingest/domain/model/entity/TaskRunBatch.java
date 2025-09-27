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
}
