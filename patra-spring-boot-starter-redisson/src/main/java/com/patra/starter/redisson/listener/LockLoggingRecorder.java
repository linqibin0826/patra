package com.patra.starter.redisson.listener;

import com.patra.starter.redisson.config.RedissonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分布式锁日志记录器
 * <p>
 * 使用 Slf4j 记录锁操作日志（DEBUG/WARN/ERROR 级别）
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockLoggingRecorder {

    private final RedissonProperties properties;

    /**
     * 记录锁获取成功
     *
     * @param lockKey    锁键
     * @param waitTimeMs 等待时间（毫秒）
     */
    public void onLockAcquired(String lockKey, long waitTimeMs) {
        String logLevel = properties.getObservability().getLogLevel();

        if ("INFO".equalsIgnoreCase(logLevel)) {
            log.info("成功获取分布式锁: key={}, waitTime={}ms", lockKey, waitTimeMs);
        } else {
            log.debug("成功获取分布式锁: key={}, waitTime={}ms", lockKey, waitTimeMs);
        }
    }

    /**
     * 记录锁获取失败
     *
     * @param lockKey 锁键
     * @param reason  失败原因
     */
    public void onLockFailed(String lockKey, String reason) {
        log.warn("获取分布式锁失败: key={}, reason={}", lockKey, reason);
    }

    /**
     * 记录锁释放
     *
     * @param lockKey    锁键
     * @param holdTimeMs 持有时间（毫秒）
     */
    public void onLockReleased(String lockKey, long holdTimeMs) {
        log.debug("释放分布式锁: key={}, holdTime={}ms", lockKey, holdTimeMs);
    }

    /**
     * 记录锁操作错误
     *
     * @param lockKey 锁键
     * @param error   错误信息
     * @param cause   异常
     */
    public void onLockError(String lockKey, String error, Throwable cause) {
        log.error("分布式锁操作错误: key={}, error={}", lockKey, error, cause);
    }
}
