package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.PlanStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * 计划聚合根：负责封装计划蓝图与状态流转。
 */
public class PlanAggregate extends AggregateRoot<Long> {

    private final Long scheduleInstanceId;
    private final String planKey;
    private final String provenanceCode;
    private final String endpointName;
    private final String operationCode;
    private final String exprProtoHash;
    private final String exprProtoSnapshotJson;
    private final String provenanceConfigSnapshotJson;
    private final String provenanceConfigHash;
    private final Instant windowFrom;
    private final Instant windowTo;
    private final String sliceStrategyCode;
    private final String sliceParamsJson;
    private PlanStatus status;

    private PlanAggregate(Long id,
                          Long scheduleInstanceId,
                          String planKey,
                          String provenanceCode,
                          String endpointName,
                          String operationCode,
                          String exprProtoHash,
                          String exprProtoSnapshotJson,
                          String provenanceConfigSnapshotJson,
                          String provenanceConfigHash,
                          Instant windowFrom,
                          Instant windowTo,
                          String sliceStrategyCode,
                          String sliceParamsJson,
                          PlanStatus status) {
        super(id);
        this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId不能为空");
        this.planKey = Objects.requireNonNull(planKey, "planKey不能为空");
        this.provenanceCode = provenanceCode;
        this.endpointName = endpointName;
        this.operationCode = operationCode;
        this.exprProtoHash = exprProtoHash;
        this.exprProtoSnapshotJson = exprProtoSnapshotJson;
        this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
        this.provenanceConfigHash = provenanceConfigHash;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.sliceStrategyCode = sliceStrategyCode;
        this.sliceParamsJson = sliceParamsJson;
        this.status = status == null ? PlanStatus.DRAFT : status;
    }

    /**
     * 创建新的计划蓝图。
     */
    public static PlanAggregate create(Long scheduleInstanceId,
                                       String planKey,
                                       String provenanceCode,
                                       String endpointName,
                                       String operationCode,
                                       String exprProtoHash,
                                       String exprProtoSnapshotJson,
                                       String provenanceConfigSnapshotJson,
                                       String provenanceConfigHash,
                                       Instant windowFrom,
                                       Instant windowTo,
                                       String sliceStrategyCode,
                                       String sliceParamsJson) {
        return new PlanAggregate(null,
                scheduleInstanceId,
                planKey,
                provenanceCode,
                endpointName,
                operationCode,
                exprProtoHash,
                exprProtoSnapshotJson,
                provenanceConfigSnapshotJson,
                provenanceConfigHash,
                windowFrom,
                windowTo,
                sliceStrategyCode,
                sliceParamsJson,
                PlanStatus.DRAFT);
    }

    /**
     * 从持久化记录重建计划聚合。
     */
    public static PlanAggregate restore(Long id,
                                        Long scheduleInstanceId,
                                        String planKey,
                                        String provenanceCode,
                                        String endpointName,
                                        String operationCode,
                                        String exprProtoHash,
                                        String exprProtoSnapshotJson,
                                        String provenanceConfigSnapshotJson,
                                        String provenanceConfigHash,
                                        Instant windowFrom,
                                        Instant windowTo,
                                        String sliceStrategyCode,
                                        String sliceParamsJson,
                                        PlanStatus status,
                                        long version) {
        PlanAggregate aggregate = new PlanAggregate(id,
                scheduleInstanceId,
                planKey,
                provenanceCode,
                endpointName,
                operationCode,
                exprProtoHash,
                exprProtoSnapshotJson,
                provenanceConfigSnapshotJson,
                provenanceConfigHash,
                windowFrom,
                windowTo,
                sliceStrategyCode,
                sliceParamsJson,
                status);
        aggregate.assignVersion(version);
        return aggregate;
    }

    public void startSlicing() {
        if (this.status != PlanStatus.DRAFT) {
            throw new IllegalStateException("计划状态非法，无法进入切片阶段");
        }
        this.status = PlanStatus.SLICING;
    }

    public void markReady() {
        this.status = PlanStatus.READY;
    }

    public void markPartial() {
        this.status = PlanStatus.PARTIAL;
    }

    public void markFailed() {
        this.status = PlanStatus.FAILED;
    }

    public void markCompleted() {
        this.status = PlanStatus.COMPLETED;
    }

    public Long getScheduleInstanceId() {
        return scheduleInstanceId;
    }

    public String getPlanKey() {
        return planKey;
    }

    public String getProvenanceCode() {
        return provenanceCode;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public String getExprProtoHash() {
        return exprProtoHash;
    }

    public String getExprProtoSnapshotJson() {
        return exprProtoSnapshotJson;
    }

    public String getProvenanceConfigSnapshotJson() {
        return provenanceConfigSnapshotJson;
    }

    public String getProvenanceConfigHash() {
        return provenanceConfigHash;
    }

    public Instant getWindowFrom() {
        return windowFrom;
    }

    public Instant getWindowTo() {
        return windowTo;
    }

    public String getSliceStrategyCode() {
        return sliceStrategyCode;
    }

    public String getSliceParamsJson() {
        return sliceParamsJson;
    }

    public PlanStatus getStatus() {
        return status;
    }
}
