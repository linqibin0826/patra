package com.patra.starter.redisson.exception;

import com.patra.common.error.ApplicationException;

/// 锁基础设施错误异常。
///
/// 当 Redis 连接失败或其他基础设施问题导致锁操作失败时抛出。
/// 客户端收到 503 Service Unavailable。
///
/// @author Patra Team
/// @since 1.0.0
public class LockInfrastructureException extends ApplicationException {

  /// 创建锁基础设施错误异常。
  ///
  /// @param lockKey 锁键
  /// @param cause   根本原因
  public LockInfrastructureException(String lockKey, Throwable cause) {
    super(
        LockErrorCode.INFRASTRUCTURE_ERROR,
        String.format("Redis 基础设施错误导致锁操作失败: %s", lockKey),
        cause);
  }

  /// 创建锁基础设施错误异常（自定义消息）。
  ///
  /// @param message 自定义消息
  /// @param lockKey 锁键
  /// @param cause   根本原因
  public LockInfrastructureException(String message, String lockKey, Throwable cause) {
    super(
        LockErrorCode.INFRASTRUCTURE_ERROR,
        String.format("%s (lockKey: %s)", message, lockKey),
        cause);
  }

  /// 创建锁基础设施错误异常（无 cause）。
  ///
  /// @param lockKey 锁键
  public LockInfrastructureException(String lockKey) {
    super(LockErrorCode.INFRASTRUCTURE_ERROR, String.format("Redis 基础设施错误导致锁操作失败: %s", lockKey));
  }
}
