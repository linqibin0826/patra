package com.patra.ingest.domain.model.event;

import lombok.Value;

import java.time.LocalDateTime;

/**
 * 调度实例已创建领域事件。
 * <p>
 * 当新的调度实例被创建时触发，表示一次新的采集编排开始。
 * 包含调度实例的基本信息和触发上下文。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class ScheduleInstanceCreatedEvent {

    /** 调度实例ID */
    Long scheduleInstanceId;

    /** 触发时间 */
    LocalDateTime triggeredAt;

    /** 表达式原型哈希 */
    String exprProtoHash;

    /** 事件发生时间 */
    LocalDateTime occurredAt;

    /**
     * 创建调度实例已创建事件。
     *
     * @param scheduleInstanceId 调度实例ID
     * @param triggeredAt        触发时间
     * @param exprProtoHash      表达式原型哈希
     * @return 领域事件
     */
    public static ScheduleInstanceCreatedEvent of(Long scheduleInstanceId,
                                                  LocalDateTime triggeredAt,
                                                  String exprProtoHash) {
        return new ScheduleInstanceCreatedEvent(
            scheduleInstanceId,
            triggeredAt,
            exprProtoHash,
            LocalDateTime.now()
        );
    }
}
