package com.patra.starter.redisson.listener;

/// 分布式锁观察者接口（SPI）。
///
/// 定义分布式锁生命周期事件的观察接口，用于可观测性集成。
/// 实现者可以记录指标、日志、追踪等。
///
/// **SPI 设计说明**：
/// - 此接口定义在 `starter-redisson` 模块
/// - 实现类（如 `LockMetricsRecorder`）在 `starter-observability` 模块
/// - 通过依赖倒置避免 redisson 编译期依赖 observability
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
