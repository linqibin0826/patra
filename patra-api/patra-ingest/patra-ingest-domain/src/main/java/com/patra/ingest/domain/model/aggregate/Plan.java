package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.SliceSpec;
import com.patra.ingest.domain.model.vo.TaskParams;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

/**
 * Plan 聚合根
 * 计划蓝图：定义总窗口与切片策略，不执行
 * 
 * @author linqibin @since 0.1.0
 */
@Getter
public class Plan {
    private final Long id;
    private final Long scheduleInstanceId;
    private final String planKey;
    private final String provenanceCode;
    private final String endpointName;
    private final String operationCode;
    private final String exprProtoHash;
    private final String exprProtoSnapshot;
    private final String provenanceConfigSnapshot;
    private final String provenanceConfigHash;
    private final Instant windowFrom;
    private final Instant windowTo;
    private final String sliceStrategyCode;
    private final String sliceParamsJson;
    private PlanStatus status;

    public Plan(Long id, 
                Long scheduleInstanceId, 
                String planKey, 
                String provenanceCode, 
                String endpointName, 
                String operationCode, 
                String exprProtoHash,
                String exprProtoSnapshot,
                String provenanceConfigSnapshot,
                String provenanceConfigHash,
                Instant windowFrom, 
                Instant windowTo,
                String sliceStrategyCode,
                String sliceParamsJson,
                PlanStatus status) {
        this.id = id;
        this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId);
        this.planKey = Objects.requireNonNull(planKey);
        this.provenanceCode = provenanceCode;
        this.endpointName = endpointName;
        this.operationCode = operationCode;
        this.exprProtoHash = exprProtoHash;
        this.exprProtoSnapshot = exprProtoSnapshot;
    this.provenanceConfigSnapshot = provenanceConfigSnapshot;
    this.provenanceConfigHash = provenanceConfigHash;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.sliceStrategyCode = sliceStrategyCode;
        this.sliceParamsJson = sliceParamsJson;
        this.status = status == null ? PlanStatus.DRAFT : status;
    }

    /**
     * 创建新计划 - 工厂方法
     */
    public static Plan create(Long scheduleInstanceId,
                              String planKey,
                              String provenanceCode,
                              String endpointName,
                              String operationCode,
                              String exprProtoHash,
                              String exprProtoSnapshot,
                              String provenanceConfigSnapshot,
                              String provenanceConfigHash,
                              Instant windowFrom,
                              Instant windowTo,
                              String sliceStrategyCode,
                              String sliceParamsJson) {
        return new Plan(
                null, // 新建时ID为空
                scheduleInstanceId,
                planKey,
                provenanceCode,
                endpointName,
                operationCode,
                exprProtoHash,
                exprProtoSnapshot,
                provenanceConfigSnapshot,
                provenanceConfigHash,
                windowFrom,
                windowTo,
                sliceStrategyCode,
                sliceParamsJson,
                PlanStatus.DRAFT
        );
    }

    /**
     * 开始切片生成
     */
    public void startSlicing() {
        if (this.status == PlanStatus.DRAFT) {
            this.status = PlanStatus.SLICING;
        }
    }

    /**
     * 标记为就绪状态
     */
    public void markReady() {
        if (this.status == PlanStatus.SLICING) {
            this.status = PlanStatus.READY;
        }
    }

    /**
     * 标记为部分完成
     */
    public void markPartial() {
        this.status = PlanStatus.PARTIAL;
    }

    /**
     * 标记为失败
     */
    public void markFailed() {
        this.status = PlanStatus.FAILED;
    }

    /**
     * 标记为完成
     */
    public void markCompleted() {
        this.status = PlanStatus.COMPLETED;
    }

    /**
     * 检查是否可以生成切片
     */
    public boolean canGenerateSlices() {
        return this.status == PlanStatus.DRAFT;
    }

    /**
     * 检查是否可以派生任务
     */
    public boolean canDeriveTasks() {
        return this.status == PlanStatus.READY;
    }
}
