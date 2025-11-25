package com.patra.starter.redisson.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/// 分布式锁 Micrometer 指标记录器。
///
/// 记录锁的等待时间、持有时间、成功/失败率。
///
/// @author Patra Team
/// @since 1.0.0
@Slf4j
@RequiredArgsConstructor
public class LockMetricsRecorder {

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
    public void onLockAcquired(String lockKey, String lockType, long waitTimeMs) {
        String keyPattern = extractKeyPattern(lockKey);

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

        log.debug("记录锁获取成功指标: key={}, pattern={}, type={}, waitTime={}ms",
            lockKey, keyPattern, lockType, waitTimeMs);
    }

    /// 记录锁获取失败。
    ///
    /// @param lockKey  锁键
    /// @param lockType 锁类型
    /// @param reason   失败原因
    public void onLockFailed(String lockKey, String lockType, String reason) {
        String keyPattern = extractKeyPattern(lockKey);

        Counter.builder(METRIC_LOCK_FAILED)
            .description("分布式锁获取失败计数")
            .tag("key_pattern", keyPattern)
            .tag("lock_type", lockType)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();

        log.debug("记录锁获取失败指标: key={}, pattern={}, type={}, reason={}",
            lockKey, keyPattern, lockType, reason);
    }

    /// 记录锁持有时间。
    ///
    /// @param lockKey    锁键
    /// @param lockType   锁类型
    /// @param holdTimeMs 持有时间（毫秒）
    public void onLockReleased(String lockKey, String lockType, long holdTimeMs) {
        String keyPattern = extractKeyPattern(lockKey);

        Timer.builder(METRIC_LOCK_HOLD_TIME)
            .description("分布式锁持有时间")
            .tag("key_pattern", keyPattern)
            .tag("lock_type", lockType)
            .register(meterRegistry)
            .record(holdTimeMs, TimeUnit.MILLISECONDS);

        log.debug("记录锁持有时间指标: key={}, pattern={}, type={}, holdTime={}ms",
            lockKey, keyPattern, lockType, holdTimeMs);
    }

    /// 从锁键中提取模式（去除动态部分）。
    ///
    /// 示例：
    /// - "patra:lock:user:123" -> "user"
    /// - "patra:lock:order:456:item:789" -> "order.item"
    /// - "catalog:lock:mesh-import:2024" -> "mesh-import"
    ///
    /// @param lockKey 完整锁键
    /// @return 锁键模式（低基数）
    private String extractKeyPattern(String lockKey) {
        if (lockKey == null || lockKey.isEmpty()) {
            return "unknown";
        }

        // 移除常见前缀（如 "patra:lock:", "catalog:lock:" 等）
        String pattern = lockKey.replaceFirst("^[a-z-]+:lock:", "");

        // 分割并提取非数字部分作为模式
        String[] parts = pattern.split(":");
        StringBuilder patternBuilder = new StringBuilder();

        for (String part : parts) {
            // 跳过纯数字、UUID、日期等动态值
            if (isStaticPart(part)) {
                if (!patternBuilder.isEmpty()) {
                    patternBuilder.append(".");
                }
                patternBuilder.append(part);
            }
        }

        return patternBuilder.isEmpty() ? "unknown" : patternBuilder.toString();
    }

    /// 判断是否为静态部分（非动态值）。
    ///
    /// @param part 键的一部分
    /// @return true 如果是静态部分
    private boolean isStaticPart(String part) {
        if (part == null || part.isEmpty()) {
            return false;
        }
        // 纯数字
        if (part.matches("^\\d+$")) {
            return false;
        }
        // UUID 格式
        if (part.matches("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")) {
            return false;
        }
        // 日期格式（如 2024-01-01, 20240101）
        if (part.matches("^\\d{4}(-\\d{2}){0,2}$") || part.matches("^\\d{8}$")) {
            return false;
        }
        return true;
    }
}
