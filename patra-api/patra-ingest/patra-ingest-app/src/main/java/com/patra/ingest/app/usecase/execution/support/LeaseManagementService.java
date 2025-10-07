package com.patra.ingest.app.usecase.execution.support;

import java.time.Duration;

/**
 * 租约管理服务。
 * <p>封装租约抢占、续租、释放逻辑（基于TaskRepository）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface LeaseManagementService {

    /**
     * 尝试抢占租约。
     *
     * @param taskId 任务ID
     * @param owner 租约持有者
     * @param leaseDuration 租约时长
     * @return true表示抢占成功
     */
    boolean tryAcquireLease(Long taskId, String owner, Duration leaseDuration);

    /**
     * 续租。
     *
     * @param taskId 任务ID
     * @param owner 租约持有者
     * @param leaseDuration 租约时长
     * @return true表示续租成功
     */
    boolean renewLease(Long taskId, String owner, Duration leaseDuration);

    /**
     * 释放租约。
     *
     * @param taskId 任务ID
     */
    void releaseLease(Long taskId);

    /**
     * 验证租约（检查owner是否仍为当前节点）。
     *
     * @param taskId 任务ID
     * @param owner 租约持有者
     * @return true表示租约仍然有效
     */
    boolean validateLease(Long taskId, String owner);
}
