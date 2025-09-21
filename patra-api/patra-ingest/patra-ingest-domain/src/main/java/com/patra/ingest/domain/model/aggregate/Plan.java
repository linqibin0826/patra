package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.PlanStatus;
import java.time.Instant;
import java.util.Objects;

/** Plan 聚合。 */
public class Plan {
    private final Long id;
    private final Long scheduleInstanceId;
    private final String planKey;
    private final String provenanceCode;
    private final String endpointName;
    private final String operationCode;
    private final String exprProtoHash;
    private final Instant windowFrom;
    private final Instant windowTo;
    private PlanStatus status;

    public Plan(Long id, Long scheduleInstanceId, String planKey, String provenanceCode, String endpointName, String operationCode, String exprProtoHash, Instant windowFrom, Instant windowTo, PlanStatus status) {
        this.id = id;
        this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId);
        this.planKey = Objects.requireNonNull(planKey);
        this.provenanceCode = provenanceCode;
        this.endpointName = endpointName;
        this.operationCode = operationCode;
        this.exprProtoHash = exprProtoHash;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.status = status == null ? PlanStatus.READY : status;
    }
    public void activate() { if (status == PlanStatus.READY) status = PlanStatus.ACTIVE; }
    public void complete() { status = PlanStatus.COMPLETED; }
    public PlanStatus getStatus() { return status; }
    public Long getId() { return id; }
    public String getProvenanceCode() { return provenanceCode; }
}
