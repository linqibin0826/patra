package com.patra.ingest.domain.model.aggregate;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.SchedulerSource;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.event.ScheduleInstanceCreatedEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 调度实例聚合根。
 * <p>
 * 承接一次外部触发（定时、手动、回放），冻结来源配置、时间窗口策略与"表达式原型"。
 * 仅描述"为什么与按照什么配置触发"，不承载执行明细。
 * </p>
 * <p>
 * 聚合根职责：
 * - 维护调度实例的完整性和不变量
 * - 管理调度实例的生命周期
 * - 产生相关的领域事件
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@Builder
public class ScheduleInstance {

    /** 聚合标识 */
    private final Long id;

    /** 调度器来源 */
    private final SchedulerSource scheduler;

    /** 外部 JobID */
    private final String schedulerJobId;

    /** 外部运行/日志ID */
    private final String schedulerLogId;

    /** 触发类型 */
    private final TriggerType triggerType;

    /** 触发时间(UTC) */
    private final LocalDateTime triggeredAt;

    /** 调度入参（规范化 JSON 字符串） */
    private final String triggerParams;

    /** 来源代码 */
    private final ProvenanceCode literatureProvenanceCode;

    /** 来源配置快照 */
    private final String provenanceConfigSnapshot;

    /** 表达式原型哈希 */
    private final String exprProtoHash;

    /** 表达式原型 AST 快照 */
    private final String exprProtoSnapshot;

    /** 领域事件列表 */
    @Builder.Default
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * 创建新的调度实例。
     *
     * @param scheduler                调度器来源
     * @param schedulerJobId          外部作业ID
     * @param schedulerLogId          外部日志ID
     * @param triggerType             触发类型
     * @param triggeredAt             触发时间
     * @param triggerParams           触发参数
     * @param literatureProvenanceCode 来源代码
     * @param provenanceConfigSnapshot 来源配置快照
     * @param exprProtoHash           表达式原型哈希
     * @param exprProtoSnapshot       表达式原型快照
     * @return 调度实例
     * @throws IllegalArgumentException 如果参数不满足业务规则
     */
    public static ScheduleInstance create(SchedulerSource scheduler,
                                        String schedulerJobId,
                                        String schedulerLogId,
                                        TriggerType triggerType,
                                        LocalDateTime triggeredAt,
                                        String triggerParams,
                                        ProvenanceCode literatureProvenanceCode,
                                        String provenanceConfigSnapshot,
                                        String exprProtoHash,
                                        String exprProtoSnapshot) {

        validateCreationParameters(scheduler, triggerType, triggeredAt, literatureProvenanceCode, exprProtoHash);

        ScheduleInstance instance = ScheduleInstance.builder()
                .scheduler(scheduler)
                .schedulerJobId(schedulerJobId)
                .schedulerLogId(schedulerLogId)
                .triggerType(triggerType)
                .triggeredAt(triggeredAt)
                .triggerParams(triggerParams)
                .literatureProvenanceCode(literatureProvenanceCode)
                .provenanceConfigSnapshot(provenanceConfigSnapshot)
                .exprProtoHash(exprProtoHash)
                .exprProtoSnapshot(exprProtoSnapshot)
                .build();

        // 添加创建事件
        instance.addDomainEvent(ScheduleInstanceCreatedEvent.of(
                instance.getId(),
                triggeredAt,
                exprProtoHash
        ));

        return instance;
    }

    /**
     * 验证创建参数。
     */
    private static void validateCreationParameters(SchedulerSource scheduler,
                                                 TriggerType triggerType,
                                                 LocalDateTime triggeredAt,
                                                 ProvenanceCode literatureProvenanceCode,
                                                 String exprProtoHash) {
        if (scheduler == null) {
            throw new IllegalArgumentException("调度器来源不能为空");
        }
        if (triggerType == null) {
            throw new IllegalArgumentException("触发类型不能为空");
        }
        if (triggeredAt == null) {
            throw new IllegalArgumentException("触发时间不能为空");
        }
        if (literatureProvenanceCode == null) {
            throw new IllegalArgumentException("来源代码不能为空");
        }
        if (exprProtoHash == null || exprProtoHash.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式原型哈希不能为空");
        }
    }

    /**
     * 获取调度标识（调度器+作业ID+日志ID的组合）。
     *
     * @return 调度标识字符串
     */
    public String getSchedulerIdentity() {
        return String.format("%s:%s:%s", scheduler, schedulerJobId, schedulerLogId);
    }

    /**
     * 检查是否为手动触发。
     *
     * @return 如果是手动触发返回 true
     */
    public boolean isManualTrigger() {
        return TriggerType.MANUAL.equals(triggerType);
    }

    /**
     * 检查是否为回放触发。
     *
     * @return 如果是回放触发返回 true
     */
    public boolean isReplayTrigger() {
        return TriggerType.REPLAY.equals(triggerType);
    }

    /**
     * 添加领域��件。
     *
     * @param event 领域事件
     */
    private void addDomainEvent(Object event) {
        domainEvents.add(event);
    }

    /**
     * 获取领域事件列表（只读）。
     *
     * @return 领域事件列表
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
