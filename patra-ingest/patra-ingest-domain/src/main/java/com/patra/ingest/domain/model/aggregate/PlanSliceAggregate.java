package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.SliceStatus;

import java.util.Objects;

/**
 * 计划切片聚合根，描述计划切片的签名与状态流转。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class PlanSliceAggregate extends AggregateRoot<Long> {

    /** 关联计划 ID */
    private Long planId;
    /** 来源编码 */
    private final String provenanceCode;
    /** 切片序号 */
    private final int sliceNo;
    /** 切片签名哈希 */
    private final String sliceSignatureHash;
    /** 窗口规格 JSON */
    private final String windowSpecJson;
    /** 局部表达式哈希 */
    private final String exprHash;
    /** 局部表达式快照 JSON */
    private final String exprSnapshotJson;
    /** 切片状态 */
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
        Objects.requireNonNull(sliceSignatureHash, "sliceSignatureHash不能为空");
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
