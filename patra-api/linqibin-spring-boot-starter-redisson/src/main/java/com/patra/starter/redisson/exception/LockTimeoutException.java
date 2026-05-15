package com.patra.starter.redisson.exception;

import dev.linqibin.commons.error.ApplicationException;

/// 锁操作超时异常。
///
/// 当锁操作超过预定时间未完成时抛出。
/// 客户端收到 500 Internal Server Error。
///
/// @author Patra Team
/// @since 1.0.0
public class LockTimeoutException extends ApplicationException {

  /// 创建锁操作超时异常。
  ///
  /// @param lockKey   锁键
  /// @param leaseTime 租约时间（毫秒）
  public LockTimeoutException(String lockKey, long leaseTime) {
    super(LockErrorCode.TIMEOUT, String.format("锁操作超时: %s（租约时间: %d ms）", lockKey, leaseTime));
  }

  /// 创建锁操作超时异常（自定义消息）。
  ///
  /// @param message   自定义消息
  /// @param lockKey   锁键
  /// @param leaseTime 租约时间（毫秒）
  public LockTimeoutException(String message, String lockKey, long leaseTime) {
    super(
        LockErrorCode.TIMEOUT,
        String.format("%s (lockKey: %s, leaseTime: %d ms)", message, lockKey, leaseTime));
  }

  /// 创建锁操作超时异常（带根本原因）。
  ///
  /// @param lockKey   锁键
  /// @param leaseTime 租约时间（毫秒）
  /// @param cause     根本原因
  public LockTimeoutException(String lockKey, long leaseTime, Throwable cause) {
    super(
        LockErrorCode.TIMEOUT, String.format("锁操作超时: %s（租约时间: %d ms）", lockKey, leaseTime), cause);
  }
}
