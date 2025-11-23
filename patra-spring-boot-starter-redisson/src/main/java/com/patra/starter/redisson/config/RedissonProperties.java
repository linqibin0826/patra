package com.patra.starter.redisson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redisson 配置属性
 * <p>
 * 集成 Redisson 官方 Starter，提供分布式锁和可观测性的自定义配置。
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "patra.redisson")
public class RedissonProperties {

    /**
     * 是否启用 Redisson Starter
     * <p>
     * 默认: true
     */
    private boolean enabled = true;

    /**
     * 看门狗超时时间（毫秒）
     * <p>
     * 如果未显式设置 leaseTime，Redisson 会使用此值作为锁的默认过期时间，
     * 并在锁持有期间自动续期（看门狗机制）。
     * <p>
     * 默认: 30000 ms（30 秒）
     */
    private long lockWatchdogTimeout = 30000;

    /**
     * 分布式锁配置
     */
    private LockProperties lock = new LockProperties();

    /**
     * 可观测性配置
     */
    private ObservabilityProperties observability = new ObservabilityProperties();

    /**
     * 分布式锁配置
     */
    @Data
    public static class LockProperties {

        /**
         * 是否启用分布式锁
         * <p>
         * 默认: true
         */
        private boolean enabled = true;

        /**
         * 默认等待时间（毫秒）
         * <p>
         * 获取锁的最大等待时间，超时未获取到锁则抛出 LockAcquisitionException。
         * <p>
         * 可通过 @DistributedLock 注解的 waitTime 属性覆盖。
         * <p>
         * 默认: 3000 ms（3 秒）
         */
        private long defaultWaitTime = 3000;

        /**
         * 默认租约时间（毫秒）
         * <p>
         * 锁的自动过期时间，防止死锁。设置为 -1 时启用看门狗机制（自动续期）。
         * <p>
         * 可通过 @DistributedLock 注解的 leaseTime 属性覆盖。
         * <p>
         * 默认: -1（启用看门狗）
         */
        private long defaultLeaseTime = -1;

        /**
         * 锁键前缀
         * <p>
         * 所有分布式锁的 Redis 键都会添加此前缀，便于统一管理和监控。
         * <p>
         * 默认: "patra:lock:"
         */
        private String keyPrefix = "patra:lock:";
    }

    /**
     * 可观测性配置
     */
    @Data
    public static class ObservabilityProperties {

        /**
         * 是否启用 Micrometer 指标
         * <p>
         * 记录锁的等待时间、持有时间、成功/失败率等指标。
         * <p>
         * 默认: true
         */
        private boolean metricsEnabled = true;

        /**
         * 是否启用分布式追踪（SkyWalking）
         * <p>
         * 为每个锁操作创建 Span，便于追踪性能瓶颈。
         * <p>
         * 默认: true
         */
        private boolean tracingEnabled = true;

        /**
         * 是否启用日志记录
         * <p>
         * 记录锁的获取、释放、失败等事件。
         * <p>
         * 默认: true
         */
        private boolean loggingEnabled = true;

        /**
         * 日志级别
         * <p>
         * 锁操作的日志级别，可选: DEBUG, INFO, WARN, ERROR
         * <p>
         * 默认: DEBUG
         */
        private String logLevel = "DEBUG";
    }
}
