package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.SliceStatus;

import java.util.Objects;

/**
 * 计划切片聚合根。
 */
public class PlanSliceAggregate extends AggregateRoot<Long> {

    private Long planId;
    private final String provenanceCode;
    private final int sequence;
    private final String sliceSignatureHash;
    private final String sliceSpecJson;
    private final String exprHash;
    private final String exprSnapshotJson;
    private SliceStatus status;

    private PlanSliceAggregate(Long id,
                               Long planId,
                               String provenanceCode,
                               int sequence,
                               String sliceSignatureHash,
                               String sliceSpecJson,
                               String exprHash,
                               String exprSnapshotJson,
                               SliceStatus status) {
        super(id);
        this.planId = planId;
        this.provenanceCode = provenanceCode;
        this.sequence = sequence;
        this.sliceSignatureHash = sliceSignatureHash;
        this.sliceSpecJson = sliceSpecJson;
        this.exprHash = exprHash;
        this.exprSnapshotJson = exprSnapshotJson;
        this.status = status == null ? SliceStatus.PENDING : status;
    }

    public static PlanSliceAggregate create(Long planId,
                                            String provenanceCode,
                                            int sequence,
                                            String sliceSignatureHash,
                                            String sliceSpecJson,
                                            String exprHash,
                                            String exprSnapshotJson) {
        Objects.requireNonNull(sliceSignatureHash, "sliceSignatureHash不能为空");
        return new PlanSliceAggregate(null,
                planId,
                provenanceCode,
                sequence,
                sliceSignatureHash,
                sliceSpecJson,
                exprHash,
                exprSnapshotJson,
                SliceStatus.PENDING);
    }

    public static PlanSliceAggregate restore(Long id,
                                             Long planId,
                                             String provenanceCode,
                                             int sequence,
                                             String sliceSignatureHash,
                                             String sliceSpecJson,
                                             String exprHash,
                                             String exprSnapshotJson,
                                             SliceStatus status,
                                             long version) {
        PlanSliceAggregate aggregate = new PlanSliceAggregate(id,
                planId,
                provenanceCode,
                sequence,
                sliceSignatureHash,
                sliceSpecJson,
                exprHash,
                exprSnapshotJson,
                status);
        aggregate.assignVersion(version);
        return aggregate;
    }

    public void bindPlan(Long planId) {
        if (planId == null) {
            throw new IllegalArgumentException("planId不能为空");
        }
        this.planId = planId;
    }

    public void markDispatched() {
        this.status = SliceStatus.DISPATCHED;
    }

    public void markExecuting() {
        this.status = SliceStatus.EXECUTING;
    }

    public void markSucceeded() {
        this.status = SliceStatus.SUCCEEDED;
    }

    public void markFailed() {
        this.status = SliceStatus.FAILED;
    }

    public void markPartial() {
        this.status = SliceStatus.PARTIAL;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getProvenanceCode() {
        return provenanceCode;
    }

    public int getSequence() {
        return sequence;
    }

    public String getSliceSignatureHash() {
        return sliceSignatureHash;
    }

    public String getSliceSpecJson() {
        return sliceSpecJson;
    }

    public String getExprHash() {
        return exprHash;
    }

    public String getExprSnapshotJson() {
        return exprSnapshotJson;
    }

    public SliceStatus getStatus() {
        return status;
    }
}
