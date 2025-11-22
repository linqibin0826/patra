package com.patra.ingest.app.usecase.execution.session;

/// 执行会话
/// 
/// 在六边形架构+DDD中的角色:应用层会话对象,封装租约、心跳和撤销标志。
/// 
/// @param taskId 任务ID
/// @param runId 运行ID
/// @param leaseOwner 租约持有者
/// @param heartbeatHandle 心跳句柄(用于停止心跳)
/// @param leaseRevoked 租约是否已被撤销(volatile标志)
/// @author linqibin
/// @since 0.1.0
public record ExecutionSession(
    Long taskId,
    Long runId,
    String leaseOwner,
    HeartbeatHandle heartbeatHandle,
    boolean leaseRevoked) {
  /// 清理资源(停止心跳 + 释放租约)
  public void cleanup() {
    if (heartbeatHandle != null) {
      heartbeatHandle.stop();
    }
  }

  /// 心跳句柄
/// 
/// 用于停止心跳和检查租约状态。
  public interface HeartbeatHandle {
    /// 停止心跳
    void stop();

    /// 返回租约是否已被撤销
/// 
/// @return true表示租约已被撤销
    boolean isLeaseRevoked();
  }
}
