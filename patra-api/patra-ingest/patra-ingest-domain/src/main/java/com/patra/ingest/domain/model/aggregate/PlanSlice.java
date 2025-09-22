package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.SliceSpec;
import lombok.Getter;

import java.util.Objects;

/**
 * Plan Slice 聚合根
 * 计划切片：并行与幂等的最小单元，承载局部化表达式
 * 
 * @author linqibin @since 0.1.0
 */
@Getter
public class PlanSlice {
    
    private final Long id;
    private final Long planId;
    private final String provenanceCode;
    private final Integer sliceNo;
    private final String sliceSignatureHash;
    private final SliceSpec sliceSpec;
    private final String exprHash;
    private final String exprSnapshot;
    private SliceStatus status;

    public PlanSlice(Long id,
                     Long planId,
                     String provenanceCode,
                     Integer sliceNo,
                     String sliceSignatureHash,
                     SliceSpec sliceSpec,
                     String exprHash,
                     String exprSnapshot,
                     SliceStatus status) {
        this.id = id;
        this.planId = Objects.requireNonNull(planId, "planId不能为空");
        this.provenanceCode = provenanceCode;
        this.sliceNo = Objects.requireNonNull(sliceNo, "sliceNo不能为空");
        this.sliceSignatureHash = Objects.requireNonNull(sliceSignatureHash, "sliceSignatureHash不能为空");
        this.sliceSpec = Objects.requireNonNull(sliceSpec, "sliceSpec不能为空");
        this.exprHash = Objects.requireNonNull(exprHash, "exprHash不能为空");
        this.exprSnapshot = exprSnapshot;
        this.status = status != null ? status : SliceStatus.PENDING;
    }

    /**
     * 创建新的切片 - 工厂方法
     */
    public static PlanSlice create(Long planId,
                                   String provenanceCode,
                                   Integer sliceNo,
                                   String sliceSignatureHash,
                                   SliceSpec sliceSpec,
                                   String exprHash,
                                   String exprSnapshot) {
        return new PlanSlice(
                null, // 新建时ID为空
                planId,
                provenanceCode,
                sliceNo,
                sliceSignatureHash,
                sliceSpec,
                exprHash,
                exprSnapshot,
                SliceStatus.PENDING
        );
    }

    /**
     * 标记为就绪状态
     */
    public void markReady() {
        if (this.status == SliceStatus.PENDING) {
            this.status = SliceStatus.SUCCEEDED;
        }
    }

    /**
     * 标记为失败状态
     */
    public void markFailed(String reason) {
        this.status = SliceStatus.FAILED;
        // 可以添加失败原因记录
    }

    /**
     * 标记为执行中
     */
    public void markExecuting() {
        if (this.status == SliceStatus.PENDING) {
            this.status = SliceStatus.EXECUTING;
        }
    }

    /**
     * 标记为成功完成
     */
    public void markSucceeded() {
        if (this.status == SliceStatus.EXECUTING) {
            this.status = SliceStatus.SUCCEEDED;
        }
    }

    /**
     * 检查是否可以派生任务
     */
    public boolean canDeriveTask() {
        return this.status == SliceStatus.PENDING;
    }
}