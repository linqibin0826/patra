package com.patra.ingest.app.usecase.execution.support;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 租约管理服务实现。
 * <p>
 * 职责：封装任务租约相关的仓储操作，提供统一的租约管理接口。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>tryAcquireLease：调用 TaskRepository.tryAcquireLease() 进行 CAS 抢占。</li>
 *   <li>renewLease：调用 TaskRepository.renewLease() 进行续租。</li>
 *   <li>releaseLease：读取任务聚合，调用 leaseInfo.release() 后保存。</li>
 *   <li>validateLease：读取任务聚合，检查 leaseInfo.owner 是否匹配。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：INFO 记录租约关键操作（抢占、释放、验证失败）。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaseManagementServiceImpl implements LeaseManagementService {

    private final TaskRepository taskRepository;
    private final Clock clock;

    /**
     * 尝试抢占租约。
     *
     * @param taskId 任务ID
     * @param owner 租约持有者
     * @param leaseDuration 租约时长
     * @return true表示抢占成功
     */
    @Override
    public boolean tryAcquireLease(Long taskId, String owner, Duration leaseDuration) {
        // 先读取任务获取幂等键（tryAcquireLease 需要幂等键参数）
        TaskAggregate task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在 taskId=" + taskId));

        Instant now = clock.instant();
        int ttlSeconds = (int) leaseDuration.toSeconds();
        boolean acquired = taskRepository.tryAcquireLease(
            taskId,
            owner,
            now,
            ttlSeconds,
            task.getIdempotentKey()
        );

        if (acquired) {
            log.info("[INGEST][APP] lease acquired taskId={} owner={}", taskId, owner);
        }
        return acquired;
    }

    /**
     * 续租。
     *
     * @param taskId 任务ID
     * @param owner 租约持有者
     * @param leaseDuration 租约时长
     * @return true表示续租成功
     */
    @Override
    public boolean renewLease(Long taskId, String owner, Duration leaseDuration) {
        Instant now = clock.instant();
        int ttlSeconds = (int) leaseDuration.toSeconds();
        return taskRepository.renewLease(taskId, owner, now, ttlSeconds);
    }

    /**
     * 释放租约。
     *
     * @param taskId 任务ID
     */
    @Override
    public void releaseLease(Long taskId) {
        TaskAggregate task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在 taskId=" + taskId));

        // 调用领域对象的 release 方法，然后保存
        task.releaseLease();
        taskRepository.save(task);
        log.info("[INGEST][APP] lease released taskId={}", taskId);
    }

    /**
     * 验证租约（检查owner是否仍为当前节点）。
     *
     * @param taskId 任务ID
     * @param owner 租约持有者
     * @return true表示租约仍然有效
     */
    @Override
    public boolean validateLease(Long taskId, String owner) {
        TaskAggregate task = taskRepository.findById(taskId)
            .orElse(null);

        if (task == null) {
            log.warn("[INGEST][APP] lease validation failed: task not found taskId={}", taskId);
            return false;
        }

        boolean valid = task.getLeaseInfo().isHeld()
            && owner.equals(task.getLeaseInfo().owner());

        if (!valid) {
            log.warn("[INGEST][APP] lease validation failed taskId={} expectedOwner={} actualOwner={}",
                     taskId, owner, task.getLeaseInfo().owner());
        }
        return valid;
    }
}
