package com.patra.ingest.app.usecase.execution.lease;

import java.time.Duration;

/// 租约管理服务。
/// 
/// 封装租约获取/续约/释放逻辑(由 TaskRepository 支持)。
/// 
/// @author linqibin
/// @since 0.1.0
public interface LeaseManagementService {

  /// 尝试获取租约。
/// 
/// @param taskId 任务 ID
/// @param owner 租约持有者
/// @param leaseDuration 租约时长
/// @return true 如果获取成功
  boolean tryAcquireLease(Long taskId, String owner, Duration leaseDuration);

  /// 续约租约。
/// 
/// @param taskId 任务 ID
/// @param owner 租约持有者
/// @param leaseDuration 租约时长
/// @return true 如果续约成功
  boolean renewLease(Long taskId, String owner, Duration leaseDuration);

  /// 释放租约。
/// 
/// @param taskId 任务 ID
  void releaseLease(Long taskId);

  /// 验证租约(持有者仍然是当前节点)。
/// 
/// @param taskId 任务 ID
/// @param owner 租约持有者
/// @return true 如果租约仍然有效
  boolean validateLease(Long taskId, String owner);
}
