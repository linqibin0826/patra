package com.patra.ingest.app.usecase.execution.lease;

import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/// 心跳续约服务实现。
///
/// 职责:使用 ScheduledExecutorService 定期续约租约。达到连续失败阈值后,验证租约以检测撤销。
///
/// 设计要点:
///
/// - 使用小型 ScheduledExecutorService 执行定期续约任务
///   - 每次续约调用 LeaseManagementService.renewLease()
///   - 连续失败 N 次(默认3次)后,调用 validateLease() 确认撤销
///   - 如果被撤销,设置 leaseRevoked 标志以便执行器中止
///   - 返回 HeartbeatHandle 用于停止心跳和查询租约状态
///
/// 配置:
///
/// - task.execution.heartbeat.failure-threshold: 连续失败阈值(默认 3)
///
/// 日志记录:
///
/// - DEBUG: 每次续约
///   - WARN: 续约失败、租约被撤销
///   - INFO: 心跳启动/停止
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatRenewalServiceImpl implements HeartbeatRenewalService {

  private final LeaseManagementService leaseManagementService;

  /// 连续失败阈值(默认 3)。
  @Value("${task.execution.heartbeat.failure-threshold:3}")
  private int failureThreshold;

  /// 全局调度器(小池;足够用于心跳任务)。
  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(
          2, // 使用 2 个线程避免单线程饥饿
          r -> {
            Thread t = new Thread(r, "heartbeat-renewal");
            t.setDaemon(true); // 守护线程;JVM 关闭时退出
            return t;
          });

  /// 启动基于心跳的租约续约。
  @Override
  public ExecutionSession.HeartbeatHandle startHeartbeat(
      Long taskId, String leaseOwner, Duration leaseDuration, Duration renewalInterval) {
    AtomicBoolean stopped = new AtomicBoolean(false);
    AtomicBoolean leaseRevoked = new AtomicBoolean(false);
    AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // 定期续约任务
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            () -> {
              if (stopped.get()) {
                return;
              }

              try {
                boolean renewed =
                    leaseManagementService.renewLease(taskId, leaseOwner, leaseDuration);

                if (renewed) {
                  consecutiveFailures.set(0); // 重置失败计数
                  if (log.isDebugEnabled()) {
                    log.debug("心跳已续约 taskId={} owner={}", taskId, leaseOwner);
                  }
                } else {
                  int failures = consecutiveFailures.incrementAndGet();
                  log.warn(
                      "心跳续约失败 taskId={} owner={} consecutiveFailures={}",
                      taskId,
                      leaseOwner,
                      failures);

                  // 达到阈值后,主动验证租约
                  if (failures >= failureThreshold) {
                    boolean valid = leaseManagementService.validateLease(taskId, leaseOwner);
                    if (!valid) {
                      leaseRevoked.set(true);
                      log.warn("检测到租约已被撤销 taskId={} owner={}", taskId, leaseOwner);
                      stopped.set(true); // 停止心跳
                    }
                  }
                }
              } catch (Exception e) {
                log.error("心跳续约错误 taskId={} owner={}", taskId, leaseOwner, e);
                int failures = consecutiveFailures.incrementAndGet();
                if (failures >= failureThreshold) {
                  leaseRevoked.set(true);
                  stopped.set(true);
                }
              }
            },
            renewalInterval.toMillis(), // 初始延迟
            renewalInterval.toMillis(), // 周期
            TimeUnit.MILLISECONDS);

    log.info(
        "心跳已启动 taskId={} owner={} interval={}ms", taskId, leaseOwner, renewalInterval.toMillis());

    return new HeartbeatHandleImpl(taskId, leaseOwner, future, stopped, leaseRevoked);
  }

  /// 心跳句柄实现。
  private static class HeartbeatHandleImpl implements ExecutionSession.HeartbeatHandle {
    private final Long taskId;
    private final String leaseOwner;
    private final ScheduledFuture<?> future;
    private final AtomicBoolean stopped;
    private final AtomicBoolean leaseRevoked;

    HeartbeatHandleImpl(
        Long taskId,
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
        future.cancel(false); // 不中断正在运行的任务
        log.info("心跳已停止 taskId={} owner={}", taskId, leaseOwner);
      }
    }

    @Override
    public boolean isLeaseRevoked() {
      return leaseRevoked.get();
    }
  }
}
