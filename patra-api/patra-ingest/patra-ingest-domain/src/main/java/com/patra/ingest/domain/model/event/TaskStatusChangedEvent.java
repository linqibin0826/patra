package com.patra.ingest.domain.model.event;

import com.patra.ingest.domain.model.enums.TaskStatus;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 任务状态已变更领域事件。
 * <p>
 * 当任务状态发生变更时触发，用于追踪任务执行进度和状态流转。
 * 包含状态变更的前后值和变更原因。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class TaskStatusChangedEvent {

    /** 任务ID */
    Long taskId;

    /** 切片ID */
    Long sliceId;

    /** 原状态 */
    TaskStatus fromStatus;

    /** 新状态 */
    TaskStatus toStatus;

    /** 变更原因 */
    String reason;

    /** 事件发生时间 */
    LocalDateTime occurredAt;

    /**
     * 创建任务状态已变更事件。
     *
     * @param taskId     任务ID
     * @param sliceId    切片ID
     * @param fromStatus 原状态
     * @param toStatus   新状态
     * @param reason     变更原因
     * @return 领域事件
     */
    public static TaskStatusChangedEvent of(Long taskId,
                                           Long sliceId,
                                           TaskStatus fromStatus,
                                           TaskStatus toStatus,
                                           String reason) {
        return new TaskStatusChangedEvent(
            taskId,
            sliceId,
            fromStatus,
            toStatus,
            reason,
            LocalDateTime.now()
        );
    }
}
