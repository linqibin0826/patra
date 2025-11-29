package com.patra.starter.redisson.listener;

/// 分布式锁观察者接口（SPI）。
///
/// 定义分布式锁生命周期事件的观察接口，用于扩展锁的可观测性。
/// 实现者可以记录指标、日志、追踪等。
///
/// **使用方式**：
/// 1. 实现此接口
/// 2. 将实现类注册为 Spring Bean
/// 3. LockExecutor 会自动注入并在锁生命周期事件时回调
///
/// @author Patra Team
/// @since 1.0.0
public interface LockObserver {

  /// 锁获取成功时回调。
  ///
  /// @param lockKey    锁键
  /// @param lockType   锁类型（REENTRANT、FAIR、READ、WRITE）
  /// @param waitTimeMs 等待时间（毫秒）
  default void onLockAcquired(String lockKey, String lockType, long waitTimeMs) {
    // 默认空实现，子类可选择性覆盖
  }

  /// 锁获取失败时回调。
  ///
  /// @param lockKey  锁键
  /// @param lockType 锁类型
  /// @param reason   失败原因（timeout、interrupted、infrastructure_error）
  default void onLockFailed(String lockKey, String lockType, String reason) {
    // 默认空实现，子类可选择性覆盖
  }

  /// 锁释放时回调。
  ///
  /// @param lockKey    锁键
  /// @param lockType   锁类型
  /// @param holdTimeMs 持有时间（毫秒）
  default void onLockReleased(String lockKey, String lockType, long holdTimeMs) {
    // 默认空实现，子类可选择性覆盖
  }
}
