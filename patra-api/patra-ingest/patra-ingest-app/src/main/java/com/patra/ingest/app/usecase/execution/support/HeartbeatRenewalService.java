package com.patra.ingest.app.usecase.execution.support;

import java.time.Duration;

/**
 * 心跳续租服务。
 * <p>使用ScheduledExecutorService定期续租，连续失败阈值后主动validateLease。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface HeartbeatRenewalService {

    /**
     * 启动心跳续租。
     *
     * @param taskId 任务ID
     * @param leaseOwner 租约持有者
     * @param leaseDuration 租约时长
     * @param renewalInterval 续租间隔
     * @return 心跳句柄（用于停止心跳）
     */
    ExecutionSession.HeartbeatHandle startHeartbeat(Long taskId,
                                                    String leaseOwner,
                                                    Duration leaseDuration,
                                                    Duration renewalInterval);
}
