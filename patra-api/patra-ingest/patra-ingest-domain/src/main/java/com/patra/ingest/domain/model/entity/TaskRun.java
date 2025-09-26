package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.model.vo.RunStats;
import java.time.Instant;

/** 任务运行 Attempt。 */
@SuppressWarnings("unused")
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
    private String error;

    public TaskRun(Long id, Long taskId, int attemptNo, String provenanceCode, String operationCode) {
        this.id = id; this.taskId = taskId; this.attemptNo = attemptNo; this.provenanceCode = provenanceCode; this.operationCode = operationCode; this.status = TaskRunStatus.PLANNED; this.stats = RunStats.empty();
    }
    public void start(Instant now) { if (status == TaskRunStatus.PLANNED) { status = TaskRunStatus.RUNNING; startedAt = now; } }
    public void appendStats(RunStats delta) { stats = stats.add(delta); }
    public void succeed(Instant now) { status = TaskRunStatus.SUCCEEDED; finishedAt = now; }
    public void fail(String err, Instant now) { status = TaskRunStatus.FAILED; error = err; finishedAt = now; }
}
