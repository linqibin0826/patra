package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.TaskParams;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * Task 聚合根
 * 任务：每个切片生成一个任务，支持强幂等与调度/执行状态
 * 
 * @author linqibin @since 0.1.0
 */
@Getter
public class Task {
    
    private final Long id;
    private final Long scheduleInstanceId;
    private final Long planId;
    private final Long sliceId;
    private final String provenanceCode;
    private final String operationCode;
    private final Long credentialId;
    private final TaskParams params;
    private final IdempotentKey idempotentKey;
    private final String exprHash;
    private final Integer priority;
    private TaskStatus status;
    private String leaseOwner;
    private Instant leasedUntil;
    private Integer leaseCount;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant finishedAt;

    public Task(Long id,
                Long scheduleInstanceId,
                Long planId,
                Long sliceId,
                String provenanceCode,
                String operationCode,
                Long credentialId,
                TaskParams params,
                IdempotentKey idempotentKey,
                String exprHash,
                Integer priority,
                TaskStatus status,
                String leaseOwner,
                Instant leasedUntil,
                Integer leaseCount,
                Instant scheduledAt,
                Instant startedAt,
                Instant finishedAt) {
        this.id = id;
        this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId不能为空");
        this.planId = Objects.requireNonNull(planId, "planId不能为空");
        this.sliceId = Objects.requireNonNull(sliceId, "sliceId不能为空");
        this.provenanceCode = Objects.requireNonNull(provenanceCode, "provenanceCode不能为空");
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode不能为空");
        this.credentialId = credentialId;
        this.params = params;
        this.idempotentKey = Objects.requireNonNull(idempotentKey, "idempotentKey不能为空");
        this.exprHash = Objects.requireNonNull(exprHash, "exprHash不能为空");
        this.priority = priority != null ? priority : 5; // 默认优先级5
        this.status = status != null ? status : TaskStatus.QUEUED;
        this.leaseOwner = leaseOwner;
        this.leasedUntil = leasedUntil;
        this.leaseCount = leaseCount != null ? leaseCount : 0;
        this.scheduledAt = scheduledAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /**
     * 创建新任务 - 工厂方法
     */
    public static Task create(Long scheduleInstanceId,
                              Long planId,
                              Long sliceId,
                              String provenanceCode,
                              String operationCode,
                              Long credentialId,
                              TaskParams params,
                              IdempotentKey idempotentKey,
                              String exprHash,
                              Integer priority,
                              Instant scheduledAt) {
        return new Task(
                null, // 新建时ID为空
                scheduleInstanceId,
                planId,
                sliceId,
                provenanceCode,
                operationCode,
                credentialId,
                params,
                idempotentKey,
                exprHash,
                priority,
                TaskStatus.QUEUED,
                null, // 未被租用
                null, // 未被租用
                0,    // 租用次数0
                scheduledAt,
                null, // 未开始
                null  // 未完成
        );
    }

    /**
     * 获取租约
     */
    public boolean acquireLease(String owner, Instant until) {
        if (this.status != TaskStatus.QUEUED) {
            return false;
        }
        
        // 检查是否已过期或未被租用
        if (this.leasedUntil == null || Instant.now().isAfter(this.leasedUntil)) {
            this.leaseOwner = owner;
            this.leasedUntil = until;
            this.leaseCount++;
            this.status = TaskStatus.RUNNING;
            this.startedAt = Instant.now();
            return true;
        }
        
        return false;
    }

    /**
     * 续租
     */
    public boolean renewLease(String owner, Instant until) {
        if (Objects.equals(this.leaseOwner, owner) && this.status == TaskStatus.RUNNING) {
            this.leasedUntil = until;
            this.leaseCount++;
            return true;
        }
        return false;
    }

    /**
     * 释放租约 - 任务完成
     */
    public void releaseLease(TaskStatus finalStatus) {
        this.leaseOwner = null;
        this.leasedUntil = null;
        this.status = finalStatus;
        this.finishedAt = Instant.now();
    }

    /**
     * 检查租约是否过期
     */
    public boolean isLeaseExpired() {
        return this.leasedUntil != null && Instant.now().isAfter(this.leasedUntil);
    }

    /**
     * 检查是否可以被调度
     */
    public boolean canBeScheduled() {
        return this.status == TaskStatus.QUEUED && 
               (this.scheduledAt == null || !Instant.now().isBefore(this.scheduledAt));
    }
}