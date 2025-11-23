package com.patra.starter.redisson.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 Micrometer 指标记录器
 * <p>
 * 记录锁的等待时间、持有时间、成功/失败率
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockMetricsRecorder {

    private final MeterRegistry meterRegistry;

    /**
     * 记录锁获取成功
     *
     * @param lockKey    锁键
     * @param waitTimeMs 等待时间（毫秒）
     */
    public void onLockAcquired(String lockKey, long waitTimeMs) {
        Counter.builder("redisson.lock.acquired")
            .tag("key", lockKey)
            .register(meterRegistry)
            .increment();

        Timer.builder("redisson.lock.wait.time")
            .tag("key", lockKey)
            .register(meterRegistry)
            .record(waitTimeMs, TimeUnit.MILLISECONDS);

        log.debug("记录锁获取成功指标: key={}, waitTime={}ms", lockKey, waitTimeMs);
    }

    /**
     * 记录锁获取失败
     *
     * @param lockKey 锁键
     * @param reason  失败原因
     */
    public void onLockFailed(String lockKey, String reason) {
        Counter.builder("redisson.lock.failed")
            .tag("key", lockKey)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();

        log.debug("记录锁获取失败指标: key={}, reason={}", lockKey, reason);
    }

    /**
     * 记录锁持有时间
     *
     * @param lockKey    锁键
     * @param holdTimeMs 持有时间（毫秒）
     */
    public void onLockReleased(String lockKey, long holdTimeMs) {
        Timer.builder("redisson.lock.hold.time")
            .tag("key", lockKey)
            .register(meterRegistry)
            .record(holdTimeMs, TimeUnit.MILLISECONDS);

        log.debug("记录锁持有时间指标: key={}, holdTime={}ms", lockKey, holdTimeMs);
    }
}
