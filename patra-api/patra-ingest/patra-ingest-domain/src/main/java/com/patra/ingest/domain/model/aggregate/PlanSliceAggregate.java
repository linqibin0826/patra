package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.SliceStatus;

import java.util.Objects;

/**
 * Aggregate root that models the signature and lifecycle of an ingestion plan slice.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class PlanSliceAggregate extends AggregateRoot<Long> {

    /** Identifier of the plan this slice belongs to. */
    private Long planId;
    /** Provenance/source code. */
    private final String provenanceCode;
    /** Slice sequence number. */
    private final int sliceNo;
    /** Slice signature hash. */
    private final String sliceSignatureHash;
    /** Window specification serialized as JSON. */
    private final String windowSpecJson;
    /** Hash of the slice-scoped expression. */
    private final String exprHash;
    /** Slice-scoped expression snapshot JSON. */
    private final String exprSnapshotJson;
    /** Current status of the slice. */
    private SliceStatus status;

    private PlanSliceAggregate(Long id,
                               Long planId,
                               String provenanceCode,
                               int sliceNo,
                               String sliceSignatureHash,
                               String windowSpecJson,
                               String exprHash,
                               String exprSnapshotJson,
                               SliceStatus status) {
        super(id);
        this.planId = planId;
        this.provenanceCode = provenanceCode;
        this.sliceNo = sliceNo;
        this.sliceSignatureHash = sliceSignatureHash;
        this.windowSpecJson = windowSpecJson;
        this.exprHash = exprHash;
        this.exprSnapshotJson = exprSnapshotJson;
        this.status = status == null ? SliceStatus.PENDING : status;
    }

    public static PlanSliceAggregate create(Long planId,
                                            String provenanceCode,
                                            int sliceNo,
                                            String sliceSignatureHash,
                                            String windowSpecJson,
                                            String exprHash,
                                            String exprSnapshotJson) {
        Objects.requireNonNull(sliceSignatureHash, "sliceSignatureHash must not be null");
        return new PlanSliceAggregate(null,
                planId,
                provenanceCode,
                sliceNo,
                sliceSignatureHash,
                windowSpecJson,
                exprHash,
                exprSnapshotJson,
                SliceStatus.PENDING);
    }

    public static PlanSliceAggregate restore(Long id,
                                             Long planId,
                                             String provenanceCode,
                                             int sequence,
                                             String sliceSignatureHash,
                                             String windowSpecJson,
                                             String exprHash,
                                             String exprSnapshotJson,
                                             SliceStatus status,
                                             long version) {
        PlanSliceAggregate aggregate = new PlanSliceAggregate(id,
                planId,
                provenanceCode,
                sequence,
                sliceSignatureHash,
                windowSpecJson,
                exprHash,
                exprSnapshotJson,
                status);
        aggregate.assignVersion(version);
        return aggregate;
    }

    public void bindPlan(Long planId) {
        if (planId == null) {
            throw new IllegalArgumentException("planId must not be null");
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

    public int getSliceNo() {
        return sliceNo;
    }

    public String getSliceSignatureHash() {
        return sliceSignatureHash;
    }

    public String getWindowSpecJson() {
        return windowSpecJson;
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
