package com.patra.ingest.app.usecase.execution.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 心跳续租服务实现。
 * <p>
 * 职责：使用 ScheduledExecutorService 定期续租，连续失败阈值后主动校验租约，检测租约丢失。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>使用单线程 ScheduledExecutorService 调度定时任务，避免并发问题。</li>
 *   <li>每次续租调用 LeaseManagementService.renewLease()。</li>
 *   <li>连续失败阈值（默认 3 次）后，调用 validateLease() 确认租约是否丢失。</li>
 *   <li>若租约丢失，设置 leaseRevoked 标志，执行层可据此中断任务。</li>
 *   <li>返回 HeartbeatHandle，用于停止心跳和查询租约状态。</li>
 * </ul>
 * </p>
 * <p>
 * 配置项：
 * <ul>
 *   <li>task.execution.heartbeat.failure-threshold：连续失败阈值，默认 3。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：
 * <ul>
 *   <li>DEBUG：每次续租操作。</li>
 *   <li>WARN：续租失败、租约丢失。</li>
 *   <li>INFO：心跳启动、停止。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatRenewalServiceImpl implements HeartbeatRenewalService {

    private final LeaseManagementService leaseManagementService;

    /** 连续失败阈值（默认 3 次） */
    @Value("${task.execution.heartbeat.failure-threshold:3}")
    private int failureThreshold;

    /** 全局调度器（单线程，足够处理所有心跳任务） */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
        2,  // 使用 2 个线程，避免单线程阻塞
        r -> {
            Thread t = new Thread(r, "heartbeat-renewal");
            t.setDaemon(true);  // 设置为守护线程，JVM 关闭时自动退出
            return t;
        }
    );

    /**
     * 启动心跳续租。
     *
     * @param taskId 任务ID
     * @param leaseOwner 租约持有者
     * @param leaseDuration 租约时长
     * @param renewalInterval 续租间隔
     * @return 心跳句柄（用于停止心跳）
     */
    @Override
    public ExecutionSession.HeartbeatHandle startHeartbeat(Long taskId,
                                                            String leaseOwner,
                                                            Duration leaseDuration,
                                                            Duration renewalInterval) {
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicBoolean leaseRevoked = new AtomicBoolean(false);
        AtomicInteger consecutiveFailures = new AtomicInteger(0);

        // 定时续租任务
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> {
                if (stopped.get()) {
                    return;
                }

                try {
                    boolean renewed = leaseManagementService.renewLease(taskId, leaseOwner, leaseDuration);

                    if (renewed) {
                        consecutiveFailures.set(0);  // 重置失败计数
                        if (log.isDebugEnabled()) {
                            log.debug("[INGEST][APP] heartbeat renewed taskId={} owner={}", taskId, leaseOwner);
                        }
                    } else {
                        int failures = consecutiveFailures.incrementAndGet();
                        log.warn("[INGEST][APP] heartbeat renewal failed taskId={} owner={} consecutiveFailures={}",
                                 taskId, leaseOwner, failures);

                        // 达到失败阈值，主动验证租约
                        if (failures >= failureThreshold) {
                            boolean valid = leaseManagementService.validateLease(taskId, leaseOwner);
                            if (!valid) {
                                leaseRevoked.set(true);
                                log.warn("[INGEST][APP] lease revoked detected taskId={} owner={}",
                                         taskId, leaseOwner);
                                stopped.set(true);  // 停止心跳
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[INGEST][APP] heartbeat renewal error taskId={} owner={}",
                              taskId, leaseOwner, e);
                    int failures = consecutiveFailures.incrementAndGet();
                    if (failures >= failureThreshold) {
                        leaseRevoked.set(true);
                        stopped.set(true);
                    }
                }
            },
            renewalInterval.toMillis(),  // 初始延迟
            renewalInterval.toMillis(),  // 周期
            TimeUnit.MILLISECONDS
        );

        log.info("[INGEST][APP] heartbeat started taskId={} owner={} interval={}ms",
                 taskId, leaseOwner, renewalInterval.toMillis());

        return new HeartbeatHandleImpl(taskId, leaseOwner, future, stopped, leaseRevoked);
    }

    /**
     * 心跳句柄实现。
     */
    private static class HeartbeatHandleImpl implements ExecutionSession.HeartbeatHandle {
        private final Long taskId;
        private final String leaseOwner;
        private final ScheduledFuture<?> future;
        private final AtomicBoolean stopped;
        private final AtomicBoolean leaseRevoked;

        HeartbeatHandleImpl(Long taskId,
                            String leaseOwner,
                            ScheduledFuture<?> future,
                            AtomicBoolean stopped,
                            AtomicBoolean leaseRevoked) {
            this.taskId = taskId;
            this.leaseOwner = leaseOwner;
            this.future = future;
            this.stopped = stopped;
            this.leaseRevoked = leaseRevoked;
        }

        @Override
        public void stop() {
            if (stopped.compareAndSet(false, true)) {
                future.cancel(false);  // 不中断正在执行的任务
                log.info("[INGEST][APP] heartbeat stopped taskId={} owner={}", taskId, leaseOwner);
            }
        }

        @Override
        public boolean isLeaseRevoked() {
            return leaseRevoked.get();
        }
    }
}
