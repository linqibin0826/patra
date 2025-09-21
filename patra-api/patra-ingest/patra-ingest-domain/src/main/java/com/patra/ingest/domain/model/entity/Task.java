package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import com.patra.ingest.domain.model.vo.TaskParams;
import java.time.Instant;
import java.util.Objects;

/** 任务。 */
public class Task {
    private final Long id;
    private final Long scheduleInstanceId;
    private final Long planId;
    private final Long sliceId;
    private final String provenanceCode;
    private final String operationCode;
    private final Long credentialId;
    private final IdempotentKey idempotentKey;
    private final String exprHash;
    private final TaskParams params;
    private TaskStatus status;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant finishedAt;

    public Task(Long id, Long scheduleInstanceId, Long planId, Long sliceId, String provenanceCode, String operationCode, Long credentialId, IdempotentKey idempotentKey, String exprHash, TaskParams params, TaskStatus status) {
        this.id = id;
        this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId);
        this.planId = Objects.requireNonNull(planId);
        this.sliceId = Objects.requireNonNull(sliceId);
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
        this.credentialId = credentialId;
        this.idempotentKey = idempotentKey;
        this.exprHash = exprHash;
        this.params = params == null ? new TaskParams(null) : params;
        this.status = status == null ? TaskStatus.QUEUED : status;
    }
    public void start(Instant now) { if (status == TaskStatus.QUEUED) { status = TaskStatus.RUNNING; startedAt = now; } }
    public void succeed(Instant now) { status = TaskStatus.SUCCEEDED; finishedAt = now; }
    public void fail(Instant now) { status = TaskStatus.FAILED; finishedAt = now; }
}
