package com.patra.starter.redisson.lock;

import lombok.Builder;
import lombok.Getter;

/// 分布式锁上下文。
///
/// 封装锁操作所需的所有参数，方便在 LockExecutor 和 Recorder 之间传递。
///
/// @author Patra Team
/// @since 1.0.0
@Getter
@Builder
public class LockContext {

    /// 完整的锁键（包含前缀）
    private final String lockKey;

    /// 锁类型
    private final LockType lockType;

    /// 等待时间（毫秒）
    private final long waitTime;

    /// 租约时间（毫秒）。
    ///
    /// -1 表示启用看门狗机制
    private final long leaseTime;

    /// 目标方法名称（用于日志和监控）
    private final String methodName;

    /// 目标类名称（用于日志和监控）
    private final String className;

    /// 锁获取开始时间戳（毫秒）
    private long lockAcquireStartTime;

    /// 锁获取成功时间戳（毫秒）
    private long lockAcquiredTime;

    /// 是否使用看门狗机制。
    ///
    /// @return true 如果 leaseTime == -1
    public boolean isWatchdogEnabled() {
        return leaseTime == -1;
    }

    /// 获取锁等待时间（毫秒）。
    ///
    /// lockAcquiredTime - lockAcquireStartTime
    ///
    /// @return 等待时间（毫秒），如果锁未获取则返回 0
    public long getActualWaitTime() {
        if (lockAcquireStartTime == 0 || lockAcquiredTime == 0) {
            return 0;
        }
        return lockAcquiredTime - lockAcquireStartTime;
    }

    /// 设置锁获取开始时间。
    ///
    /// @param timestamp 时间戳（毫秒）
    public void markAcquireStart(long timestamp) {
        this.lockAcquireStartTime = timestamp;
    }

    /// 设置锁获取成功时间。
    ///
    /// @param timestamp 时间戳（毫秒）
    public void markAcquired(long timestamp) {
        this.lockAcquiredTime = timestamp;
    }

    /// 检查锁是否已被获取。
    ///
    /// @return 如果锁已被获取返回 true，否则返回 false
    public boolean isAcquired() {
        return this.lockAcquiredTime > 0;
    }
}
