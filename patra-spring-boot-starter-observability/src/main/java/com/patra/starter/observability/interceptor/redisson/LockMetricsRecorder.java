package com.patra.starter.observability.interceptor.redisson;

import com.patra.starter.redisson.listener.LockKeyPatternExtractor;
import com.patra.starter.redisson.listener.LockObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/// 分布式锁 Micrometer 指标记录器。
///
/// 实现 `LockObserver` SPI 接口，记录锁的等待时间、持有时间、成功/失败率。
///
/// 指标列表：
///
/// - `patra.redisson.lock.acquired` - 锁获取成功计数
/// - `patra.redisson.lock.failed` - 锁获取失败计数
/// - `patra.redisson.lock.wait_time` - 锁等待时间
/// - `patra.redisson.lock.hold_time` - 锁持有时间
///
/// 标签：
///
/// - `key_pattern` - 锁键模式（低基数，去除动态部分）
/// - `lock_type` - 锁类型（REENTRANT、FAIR、READ、WRITE）
/// - `reason` - 失败原因（仅失败指标）
///
/// @author Patra Team
/// @since 1.0.0
@Slf4j
@RequiredArgsConstructor
public class LockMetricsRecorder implements LockObserver {

  /// Micrometer 指标注册表
  private final MeterRegistry meterRegistry;

  /// 指标名称常量。
  private static final String METRIC_LOCK_ACQUIRED = "patra.redisson.lock.acquired";
  private static final String METRIC_LOCK_FAILED = "patra.redisson.lock.failed";
  private static final String METRIC_LOCK_WAIT_TIME = "patra.redisson.lock.wait_time";
  private static final String METRIC_LOCK_HOLD_TIME = "patra.redisson.lock.hold_time";

  /// 记录锁获取成功。
  ///
  /// @param lockKey    锁键
  /// @param lockType   锁类型
  /// @param waitTimeMs 等待时间（毫秒）
  @Override
  public void onLockAcquired(String lockKey, String lockType, long waitTimeMs) {
    String keyPattern = LockKeyPatternExtractor.extract(lockKey);

    Counter.builder(METRIC_LOCK_ACQUIRED)
        .description("分布式锁获取成功计数")
        .tag("key_pattern", keyPattern)
        .tag("lock_type", lockType)
        .register(meterRegistry)
        .increment();

    Timer.builder(METRIC_LOCK_WAIT_TIME)
        .description("分布式锁等待时间")
        .tag("key_pattern", keyPattern)
        .tag("lock_type", lockType)
        .register(meterRegistry)
        .record(waitTimeMs, TimeUnit.MILLISECONDS);

    log.debug(
        "记录锁获取成功指标: key={}, pattern={}, type={}, waitTime={}ms",
        lockKey,
        keyPattern,
        lockType,
        waitTimeMs);
  }

  /// 记录锁获取失败。
  ///
  /// @param lockKey  锁键
  /// @param lockType 锁类型
  /// @param reason   失败原因
  @Override
  public void onLockFailed(String lockKey, String lockType, String reason) {
    String keyPattern = LockKeyPatternExtractor.extract(lockKey);

    Counter.builder(METRIC_LOCK_FAILED)
        .description("分布式锁获取失败计数")
        .tag("key_pattern", keyPattern)
        .tag("lock_type", lockType)
        .tag("reason", reason)
        .register(meterRegistry)
        .increment();

    log.debug(
        "记录锁获取失败指标: key={}, pattern={}, type={}, reason={}", lockKey, keyPattern, lockType, reason);
  }

  /// 记录锁持有时间。
  ///
  /// @param lockKey    锁键
  /// @param lockType   锁类型
  /// @param holdTimeMs 持有时间（毫秒）
  @Override
  public void onLockReleased(String lockKey, String lockType, long holdTimeMs) {
    String keyPattern = LockKeyPatternExtractor.extract(lockKey);

    Timer.builder(METRIC_LOCK_HOLD_TIME)
        .description("分布式锁持有时间")
        .tag("key_pattern", keyPattern)
        .tag("lock_type", lockType)
        .register(meterRegistry)
        .record(holdTimeMs, TimeUnit.MILLISECONDS);

    log.debug(
        "记录锁持有时间指标: key={}, pattern={}, type={}, holdTime={}ms",
        lockKey,
        keyPattern,
        lockType,
        holdTimeMs);
  }
}
