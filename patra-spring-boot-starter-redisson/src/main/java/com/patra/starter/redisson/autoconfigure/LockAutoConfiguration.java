package com.patra.starter.redisson.autoconfigure;

import com.patra.starter.redisson.config.RedissonProperties;
import com.patra.starter.redisson.listener.LockLoggingRecorder;
import com.patra.starter.redisson.listener.LockMetricsRecorder;
import com.patra.starter.redisson.lock.LockAspect;
import com.patra.starter.redisson.lock.LockExecutor;
import com.patra.starter.redisson.lock.LockKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// 分布式锁自动配置。
///
/// 配置所有锁相关的 Bean（LockKeyGenerator、LockExecutor、LockAspect）。
/// 自动集成可观测性组件（如果已启用）。
///
/// @author Patra Team
/// @since 1.0.0
@Slf4j
@AutoConfiguration(after = {RedissonAutoConfiguration.class, ObservabilityAutoConfiguration.class})
@ConditionalOnClass({RedissonClient.class, RLock.class})
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(prefix = "patra.redisson.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
public class LockAutoConfiguration {

    /// 配置锁键生成器。
    ///
    /// @param properties Redisson 配置属性
    /// @return LockKeyGenerator
    @Bean
    public LockKeyGenerator lockKeyGenerator(RedissonProperties properties) {
        log.info("初始化 LockKeyGenerator");
        return new LockKeyGenerator(properties);
    }

    /// 配置锁执行器。
    ///
    /// 自动注入可观测性组件（如果已启用），否则为 null。
    ///
    /// @param redissonClient   Redisson 客户端
    /// @param metricsRecorder  指标记录器（可选）
    /// @param loggingRecorder  日志记录器（可选）
    /// @return LockExecutor
    @Bean
    public LockExecutor lockExecutor(
        RedissonClient redissonClient,
        @Autowired(required = false) LockMetricsRecorder metricsRecorder,
        @Autowired(required = false) LockLoggingRecorder loggingRecorder
    ) {
        log.info("初始化 LockExecutor (metrics={}, logging={})",
            metricsRecorder != null ? "启用" : "禁用",
            loggingRecorder != null ? "启用" : "禁用");
        return new LockExecutor(redissonClient, metricsRecorder, loggingRecorder);
    }

    /// 配置锁切面。
    ///
    /// @param lockKeyGenerator 锁键生成器
    /// @param lockExecutor     锁执行器
    /// @param properties       Redisson 配置属性
    /// @return LockAspect
    @Bean
    public LockAspect lockAspect(
        LockKeyGenerator lockKeyGenerator,
        LockExecutor lockExecutor,
        RedissonProperties properties
    ) {
        log.info("初始化 LockAspect (AOP 切面)");
        return new LockAspect(lockKeyGenerator, lockExecutor, properties);
    }
}
