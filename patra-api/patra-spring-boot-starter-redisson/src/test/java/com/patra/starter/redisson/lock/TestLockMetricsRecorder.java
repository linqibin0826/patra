package com.patra.starter.redisson.lock;

import com.patra.starter.redisson.listener.LockKeyPatternExtractor;
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
    }

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
    }

    @Override
    public void onLockReleased(String lockKey, String lockType, long holdTimeMs) {
        String keyPattern = LockKeyPatternExtractor.extract(lockKey);

        Timer.builder(METRIC_LOCK_HOLD_TIME)
            .description("分布式锁持有时间")
            .tag("key_pattern", keyPattern)
            .tag("lock_type", lockType)
            .register(meterRegistry)
            .record(holdTimeMs, TimeUnit.MILLISECONDS);
    }
}
