package dev.linqibin.patra.ingest.app.usecase.execution.lease;

import dev.linqibin.patra.ingest.app.usecase.execution.session.ExecutionSession;
import java.time.Duration;

/// 心跳续约服务。
///
/// 使用 ScheduledExecutorService 定期续约租约;连续失败后验证租约。
///
/// @author linqibin
/// @since 0.1.0
public interface HeartbeatRenewalService {

  /// 启动基于心跳的租约续约。
  ///
  /// @param taskId 任务 ID
  /// @param leaseOwner 租约持有者
  /// @param leaseDuration 租约时长
  /// @param renewalInterval 续约间隔
  /// @return 心跳句柄(用于停止心跳)
  ExecutionSession.HeartbeatHandle startHeartbeat(
      Long taskId, String leaseOwner, Duration leaseDuration, Duration renewalInterval);
}
