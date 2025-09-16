package com.patra.ingest.domain.model.aggregate;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.event.TaskStatusChangedEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 任务聚合根。
 * <p>
 * 通常"一片一任务"，是对切片执行的具体指令（来源代码 + operation），包含执行参数与幂等键。
 * 状态：queued → running → succeeded/failed/cancelled；支持优先级与调度/开始/结束时间。
 * </p>
 * <p>
 * 聚合根职责：
 * - 管理任务状态流转和不变量
 * - 确保任务执行的一致性
 * - 产生任务状态变更事件
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@Builder
public class Task {

    /** 聚合标识 */
    private final Long id;

    /** 调度实例ID */
    private final Long scheduleInstanceId;

    /** 计划ID */
    private final Long planId;

    /** 切片ID */
    private final Long sliceId;

    /** 来源代码 */
    private final ProvenanceCode literatureProvenanceCode;

    /** 操作类型 */
    private final IngestOperationType operation;

    /** API凭据ID */
    private final Long apiCredentialId;

    /** 任务参数（JSON字符串） */
    private final String params;

    /** 幂等键 */
    private final String idempotentKey;

    /** 表达式哈希 */
    private final String exprHash;

    /** 优先级（1高→9低） */
    private final Integer priority;

    /** 任务状态 */
    private TaskStatus status;

    /** 计划开��时间 */
    private final LocalDateTime scheduledAt;

    /** 实际开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 外部调度运行ID */
    private final String schedulerRunId;

    /** 关联ID */
    private final String correlationId;

    /** 领域事件列表 */
    @Builder.Default
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * 创建新任务。
     *
     * @param scheduleInstanceId       调度实例ID
     * @param planId                  计划ID
     * @param sliceId                 切片ID
     * @param literatureProvenanceCode 来源��码
     * @param operation               操作类型
     * @param params                  任务参数
     * @param idempotentKey           幂等键
     * @param exprHash                表达式哈希
     * @param priority                优先级
     * @param scheduledAt             计划开始时间
     * @return 任务实例
     */
    public static Task create(Long scheduleInstanceId,
                             Long planId,
                             Long sliceId,
                             ProvenanceCode literatureProvenanceCode,
                             IngestOperationType operation,
                             String params,
                             String idempotentKey,
                             String exprHash,
                             Integer priority,
                             LocalDateTime scheduledAt) {

        validateCreationParameters(sliceId, literatureProvenanceCode, operation, idempotentKey, exprHash);

        return Task.builder()
                .scheduleInstanceId(scheduleInstanceId)
                .planId(planId)
                .sliceId(sliceId)
                .literatureProvenanceCode(literatureProvenanceCode)
                .operation(operation)
                .params(params)
                .idempotentKey(idempotentKey)
                .exprHash(exprHash)
                .priority(priority != null ? priority : 5) // 默认优先级5
                .status(TaskStatus.QUEUED) // 初始状态为排队
                .scheduledAt(scheduledAt)
                .build();
    }

    /**
     * 验证创建参数。
     */
    private static void validateCreationParameters(Long sliceId,
                                                 ProvenanceCode literatureProvenanceCode,
                                                 IngestOperationType operation,
                                                 String idempotentKey,
                                                 String exprHash) {
        if (sliceId == null) {
            throw new IllegalArgumentException("切片ID不能为空");
        }
        if (literatureProvenanceCode == null) {
            throw new IllegalArgumentException("来源代码不能为空");
        }
        if (operation == null) {
            throw new IllegalArgumentException("操作类型不能为空");
        }
        if (idempotentKey == null || idempotentKey.trim().isEmpty()) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        if (exprHash == null || exprHash.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式哈希不能为空");
        }
    }

    /**
     * 开始执行任务。
     *
     * @throws IllegalStateException 如果任务状态不允许开始执行
     */
    public void start() {
        if (!TaskStatus.QUEUED.equals(status)) {
            throw new IllegalStateException("只有排队状态的任务才能开始执行");
        }

        TaskStatus oldStatus = this.status;
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();

        addDomainEvent(TaskStatusChangedEvent.of(id, sliceId, oldStatus, status, "任务开始执行"));
    }

    /**
     * 标记任务成功完成。
     *
     * @throws IllegalStateException 如果任务状态不允许完成
     */
    public void succeed() {
        if (!TaskStatus.RUNNING.equals(status)) {
            throw new IllegalStateException("只有运行中的任务才能标记为成功");
        }

        TaskStatus oldStatus = this.status;
        this.status = TaskStatus.SUCCEEDED;
        this.finishedAt = LocalDateTime.now();

        addDomainEvent(TaskStatusChangedEvent.of(id, sliceId, oldStatus, status, "任务执行成功"));
    }

    /**
     * 标记任务失败。
     *
     * @param reason 失败原因
     * @throws IllegalStateException 如果任务状态不允许失败
     */
    public void fail(String reason) {
        if (!TaskStatus.RUNNING.equals(status)) {
            throw new IllegalStateException("只有运行中的任务才能标记为失败");
        }

        TaskStatus oldStatus = this.status;
        this.status = TaskStatus.FAILED;
        this.finishedAt = LocalDateTime.now();

        addDomainEvent(TaskStatusChangedEvent.of(id, sliceId, oldStatus, status,
                reason != null ? reason : "任务执行失败"));
    }

    /**
     * 取消任务。
     *
     * @param reason 取消原因
     * @throws IllegalStateException 如果任务状态不允许取消
     */
    public void cancel(String reason) {
        if (TaskStatus.SUCCEEDED.equals(status) || TaskStatus.FAILED.equals(status)) {
            throw new IllegalStateException("已完成的任务不能被取消");
        }

        TaskStatus oldStatus = this.status;
        this.status = TaskStatus.CANCELLED;
        this.finishedAt = LocalDateTime.now();

        addDomainEvent(TaskStatusChangedEvent.of(id, sliceId, oldStatus, status,
                reason != null ? reason : "任务被取消"));
    }

    /**
     * 检查任务是否已完成（成功、失败或取消）。
     *
     * @return 如果任务已完成返回 true
     */
    public boolean isCompleted() {
        return TaskStatus.SUCCEEDED.equals(status) ||
               TaskStatus.FAILED.equals(status) ||
               TaskStatus.CANCELLED.equals(status);
    }

    /**
     * 检查任务是否成功。
     *
     * @return 如果任务成功返回 true
     */
    public boolean isSucceeded() {
        return TaskStatus.SUCCEEDED.equals(status);
    }

    /**
     * 检查任务是否正在运行。
     *
     * @return 如果任务正在运行返回 true
     */
    public boolean isRunning() {
        return TaskStatus.RUNNING.equals(status);
    }

    /**
     * 添加领域事件。
     */
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
