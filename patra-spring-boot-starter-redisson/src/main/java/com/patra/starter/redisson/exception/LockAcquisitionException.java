package com.patra.starter.redisson.exception;

import com.patra.common.error.ApplicationException;

/// 锁获取失败异常。
///
/// 当无法在指定时间内获取分布式锁时抛出。
/// 客户端收到 409 Conflict，可选择重试。
///
/// @author Patra Team
/// @since 1.0.0
public class LockAcquisitionException extends ApplicationException {

    /// 创建锁获取失败异常。
    ///
    /// @param lockKey  锁键
    /// @param waitTime 等待时间（毫秒）
    public LockAcquisitionException(String lockKey, long waitTime) {
        super(
            LockErrorCode.ACQUISITION_FAILED,
            String.format("无法获取分布式锁: %s（等待时间: %d ms）", lockKey, waitTime)
        );
    }

    /// 创建锁获取失败异常（自定义消息）。
    ///
    /// @param message  自定义消息
    /// @param lockKey  锁键
    /// @param waitTime 等待时间（毫秒）
    public LockAcquisitionException(String message, String lockKey, long waitTime) {
        super(
            LockErrorCode.ACQUISITION_FAILED,
            String.format("%s (lockKey: %s, waitTime: %d ms)", message, lockKey, waitTime)
        );
    }

    /// 创建锁获取失败异常（带根本原因）。
    ///
    /// @param lockKey  锁键
    /// @param waitTime 等待时间（毫秒）
    /// @param cause    根本原因
    public LockAcquisitionException(String lockKey, long waitTime, Throwable cause) {
        super(
            LockErrorCode.ACQUISITION_FAILED,
            String.format("无法获取分布式锁: %s（等待时间: %d ms）", lockKey, waitTime),
            cause
        );
    }
}
