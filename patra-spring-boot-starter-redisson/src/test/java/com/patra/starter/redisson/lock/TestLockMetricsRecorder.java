package com.patra.starter.redisson.lock;

import com.patra.starter.redisson.listener.LockObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * 测试用分布式锁指标记录器。
 *
 * 实现 {@link LockObserver} 接口，在集成测试中记录锁指标。
 *
 * @author Patra Team
 * @since 1.0.0
 */
public class TestLockMetricsRecorder implements LockObserver {

    private final MeterRegistry meterRegistry;

    private static final String METRIC_LOCK_ACQUIRED = "patra.redisson.lock.acquired";
    private static final String METRIC_LOCK_FAILED = "patra.redisson.lock.failed";
    private static final String METRIC_LOCK_WAIT_TIME = "patra.redisson.lock.wait_time";
    private static final String METRIC_LOCK_HOLD_TIME = "patra.redisson.lock.hold_time";

    public TestLockMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
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
    }

    @Override
    public void onLockFailed(String lockKey, String lockType, String reason) {
        String keyPattern = extractKeyPattern(lockKey);

        Counter.builder(METRIC_LOCK_FAILED)
            .description("分布式锁获取失败计数")
            .tag("key_pattern", keyPattern)
            .tag("lock_type", lockType)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }

    @Override
    public void onLockReleased(String lockKey, String lockType, long holdTimeMs) {
        String keyPattern = extractKeyPattern(lockKey);

        Timer.builder(METRIC_LOCK_HOLD_TIME)
            .description("分布式锁持有时间")
            .tag("key_pattern", keyPattern)
            .tag("lock_type", lockType)
            .register(meterRegistry)
            .record(holdTimeMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 从锁键中提取模式（去除动态部分）。
     */
    private String extractKeyPattern(String lockKey) {
        if (lockKey == null || lockKey.isEmpty()) {
            return "unknown";
        }

        // 移除常见前缀
        String pattern = lockKey.replaceFirst("^[a-z-]+:lock:", "");

        // 分割并提取非数字部分作为模式
        String[] parts = pattern.split(":");
        StringBuilder patternBuilder = new StringBuilder();

        for (String part : parts) {
            if (isStaticPart(part)) {
                if (!patternBuilder.isEmpty()) {
                    patternBuilder.append(".");
                }
                patternBuilder.append(part);
            }
        }

        return patternBuilder.isEmpty() ? "unknown" : patternBuilder.toString();
    }

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
        // 日期格式
        if (part.matches("^\\d{4}(-\\d{2}){0,2}$") || part.matches("^\\d{8}$")) {
            return false;
        }
        return true;
    }
}
