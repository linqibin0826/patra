package com.patra.ingest.domain.model.aggregate;

import java.time.Instant;
import java.util.Objects;

/** 调度实例聚合根。 */
public class ScheduleInstance {
    private final Long id; // 可为空(未持久化)
    private final String schedulerCode;
    private final String schedulerJobId;
    private final String schedulerLogId;
    private final String triggerTypeCode;
    private final Instant triggeredAt;
    private final String provenanceCode;
    private final String provenanceConfigSnapshotJson;
    private final String exprProtoHash;
    private final String exprProtoSnapshotJson;

    public ScheduleInstance(Long id,
                            String schedulerCode,
                            String schedulerJobId,
                            String schedulerLogId,
                            String triggerTypeCode,
                            Instant triggeredAt,
                            String provenanceCode,
                            String provenanceConfigSnapshotJson,
                            String exprProtoHash,
                            String exprProtoSnapshotJson) {
        this.id = id;
        this.schedulerCode = Objects.requireNonNull(schedulerCode);
        this.schedulerJobId = schedulerJobId;
        this.schedulerLogId = schedulerLogId;
        this.triggerTypeCode = Objects.requireNonNull(triggerTypeCode);
        this.triggeredAt = Objects.requireNonNull(triggeredAt);
        this.provenanceCode = Objects.requireNonNull(provenanceCode);
        this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
        this.exprProtoHash = exprProtoHash;
        this.exprProtoSnapshotJson = exprProtoSnapshotJson;
    }

    public Long getId() { return id; }
    public String getProvenanceCode() { return provenanceCode; }
    public String getExprProtoHash() { return exprProtoHash; }
}
