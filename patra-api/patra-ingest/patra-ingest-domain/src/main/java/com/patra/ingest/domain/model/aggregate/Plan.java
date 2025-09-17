package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.ingest.domain.model.vo.TimeWindow;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 计划蓝图聚合根。
 * <p>
 * 在某个调度实例下，定义一次采集的总窗口与切片策略，对应表达式原型层（不含局部化切片条件）。
 * 生命周期：draft → ready → active → completed/aborted。
 * </p>
 * <p>
 * 聚合根职责：
 * - 管理计划的状态流转和生命周期
 * - 维护时间窗口和切片策略的一致性
 * - 确保计划配���的完整性
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@Builder
public class Plan {

    /** 聚合标识 */
    private final Long id;

    /** 关联调度实例ID */
    private final Long scheduleInstanceId;

    /** 人类可读/对外幂等标识 */
    private final String planKey;

    /** 表达式原型哈希 */
    private final String exprProtoHash;

    /** 表达式原型 AST 快照 */
    private final String exprProtoSnapshot;

    /** 计划类型 */
    private final IngestOperationType operation;

    /** 总窗起(UTC, 含) */
    private final LocalDateTime windowFrom;

    /** 总窗止(UTC, 不含) */
    private final LocalDateTime windowTo;

    /** 切片策略 */
    private final SliceStrategy sliceStrategy;

    /** 切片参数（JSON 字符串） */
    private final String sliceParams;

    /** 计划状态 */
    private PlanStatus status;

    /** 领域事件列表 */
    @Builder.Default
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * 创建新的计划蓝图。
     *
     * @param scheduleInstanceId  调度实例ID
     * @param planKey            计划键
     * @param exprProtoHash      表达式原型哈希
     * @param exprProtoSnapshot  表达式原型快照
     * @param windowFrom         窗口起始时间
     * @param windowTo           窗口结束时间
     * @param sliceStrategy      切片策略
     * @param sliceParams        切片参数
     * @return 计划实例
     */
    public static Plan create(Long scheduleInstanceId,
                             String planKey,
                             String exprProtoHash,
                             String exprProtoSnapshot,
                             LocalDateTime windowFrom,
                             LocalDateTime windowTo,
                             SliceStrategy sliceStrategy,
                             String sliceParams,
                             IngestOperationType operation) {

        validateCreationParameters(scheduleInstanceId, planKey, exprProtoHash,
                                 windowFrom, windowTo, sliceStrategy);

    return Plan.builder()
                .scheduleInstanceId(scheduleInstanceId)
                .planKey(planKey)
                .exprProtoHash(exprProtoHash)
                .exprProtoSnapshot(exprProtoSnapshot)
                .windowFrom(windowFrom)
                .windowTo(windowTo)
                .sliceStrategy(sliceStrategy)
                .sliceParams(sliceParams)
        .operation(operation)
                .status(PlanStatus.DRAFT) // 初始状态为草稿
                .build();
    }

    /**
     * 验证创建参数。
     */
    private static void validateCreationParameters(Long scheduleInstanceId,
                                                 String planKey,
                                                 String exprProtoHash,
                                                 LocalDateTime windowFrom,
                                                 LocalDateTime windowTo,
                                                 SliceStrategy sliceStrategy) {
        if (scheduleInstanceId == null) {
            throw new IllegalArgumentException("调度实例ID不能为空");
        }
        if (planKey == null || planKey.trim().isEmpty()) {
            throw new IllegalArgumentException("计划键不能为空");
        }
        if (exprProtoHash == null || exprProtoHash.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式原型哈希不能为空");
        }
        if (windowFrom != null && windowTo != null && windowFrom.isAfter(windowTo)) {
            throw new IllegalArgumentException("窗口起始时间不能晚于结束时间");
        }
        if (sliceStrategy == null) {
            throw new IllegalArgumentException("切片策略不能为空");
        }
    }

    /**
     * 准备计划（从草稿状态转为就绪状态）。
     *
     * @throws IllegalStateException 如果当前状态不允许转为就绪
     */
    public void ready() {
        if (!PlanStatus.DRAFT.equals(status)) {
            throw new IllegalStateException("只有草稿状态的计划才能转为就绪状态");
        }
        this.status = PlanStatus.READY;
    }

    /**
     * 激活计划（开始执行）。
     *
     * @throws IllegalStateException 如果当前状态不允许激活
     */
    public void activate() {
        if (!PlanStatus.READY.equals(status)) {
            throw new IllegalStateException("只有就绪状态的计划才能被激活");
        }
        this.status = PlanStatus.ACTIVE;
    }

    /**
     * 完成计划。
     *
     * @throws IllegalStateException 如果当前状态不允许完成
     */
    public void complete() {
        if (!PlanStatus.ACTIVE.equals(status)) {
            throw new IllegalStateException("只有激活状态的计划才能被完成");
        }
        this.status = PlanStatus.COMPLETED;
    }

    /**
     * 中止计划。
     *
     * @param reason 中止原因
     * @throws IllegalStateException 如果当前状态不允许中止
     */
    public void abort(String reason) {
        if (PlanStatus.COMPLETED.equals(status) || PlanStatus.ABORTED.equals(status)) {
            throw new IllegalStateException("已完成或已中止的计划不能再次中止");
        }
        this.status = PlanStatus.ABORTED;
    }

    /**
     * 获取时间窗口值对象。
     *
     * @return 时间窗口
     */
    public TimeWindow getTimeWindow() {
        return TimeWindow.of(windowFrom, windowTo);
    }

    /**
     * 检查计划是否已完成。
     *
     * @return 如果计划已完成或中止返回 true
     */
    public boolean isFinished() {
        return PlanStatus.COMPLETED.equals(status) || PlanStatus.ABORTED.equals(status);
    }

    /**
     * 检查计划是否正在执行。
     *
     * @return 如果计划处于激活状态返回 true
     */
    public boolean isActive() {
        return PlanStatus.ACTIVE.equals(status);
    }

    /**
     * 检查计划是否可以开始执行。
     *
     * @return 如果计划处于就绪状态返回 true
     */
    public boolean isReady() {
        return PlanStatus.READY.equals(status);
    }

    /**
     * 添加领域事件。
     */
    @SuppressWarnings("unused")
    private void addDomainEvent(Object event) {
        domainEvents.add(event);
    }

    /**
     * 获取领域事件列表（只读）。
     */
    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 清除领域事件。
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
