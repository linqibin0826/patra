package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.BatchStats;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import java.time.Instant;

/** 单次运行的分页/令牌批次。 */
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
    private final String afterToken;
    private final IdempotentKey idempotentKey;
    private BatchStatus status;
    private BatchStats stats;
    private Instant committedAt;
    private String error;

    public TaskRunBatch(Long id, Long runId, Long taskId, Long sliceId, Long planId, String provenanceCode, String operationCode, int batchNo, Integer pageNo, Integer pageSize, String beforeToken, IdempotentKey idempotentKey) {
        this.id = id; this.runId = runId; this.taskId = taskId; this.sliceId = sliceId; this.planId = planId; this.provenanceCode = provenanceCode; this.operationCode = operationCode; this.batchNo = batchNo; this.pageNo = pageNo; this.pageSize = pageSize; this.beforeToken = beforeToken; this.idempotentKey = idempotentKey; this.status = BatchStatus.RUNNING; this.stats = BatchStats.of(0);
        this.afterToken = null;
    }
    public void succeed(int count, String afterTok, Instant now) { this.stats = BatchStats.of(count); this.status = BatchStatus.SUCCEEDED; this.committedAt = now; }
    public void fail(String err, Instant now) { this.status = BatchStatus.FAILED; this.error = err; this.committedAt = now; }
}
