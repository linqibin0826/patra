package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.SliceSpec;
import java.util.Objects;

/** 计划切片。 */
public class PlanSlice {
    private final Long id;
    private final Long planId;
    private final String provenanceCode;
    private final int sliceNo;
    private final String sliceSignatureHash;
    private final String exprHash;
    private final SliceSpec spec;
    private SliceStatus status;

    public PlanSlice(Long id, Long planId, String provenanceCode, int sliceNo, String sliceSignatureHash, String exprHash, SliceSpec spec, SliceStatus status) {
        this.id = id;
        this.planId = Objects.requireNonNull(planId);
        this.provenanceCode = provenanceCode;
        this.sliceNo = sliceNo;
        this.sliceSignatureHash = sliceSignatureHash;
        this.exprHash = exprHash;
        this.spec = spec;
        this.status = status == null ? SliceStatus.PENDING : status;
    }
    public void markDispatched() { if (status == SliceStatus.PENDING) status = SliceStatus.DISPATCHED; }
    public void markSucceeded() { status = SliceStatus.SUCCEEDED; }
    public void markFailed() { status = SliceStatus.FAILED; }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getProvenanceCode() { return provenanceCode; }
    public int getSliceNo() { return sliceNo; }
    public String getSliceSignatureHash() { return sliceSignatureHash; }
    public String getExprHash() { return exprHash; }
    public SliceSpec getSpec() { return spec; }
    public SliceStatus getStatus() { return status; }
}
