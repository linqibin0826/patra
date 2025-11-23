package com.patra.starter.redisson.autoconfigure;

import com.patra.starter.redisson.config.RedissonProperties;
import com.patra.starter.redisson.listener.LockLoggingRecorder;
import com.patra.starter.redisson.listener.LockMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 可观测性自动配置
 * <p>
 * 配置可观测性 Recorder（Metrics、Logging）
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(after = LockAutoConfiguration.class)
@EnableConfigurationProperties(RedissonProperties.class)
public class ObservabilityAutoConfiguration {

    /**
     * 配置 Micrometer 指标记录器
     *
     * @param meterRegistry Micrometer 注册表
     * @return LockMetricsRecorder
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "patra.redisson.observability", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
    public LockMetricsRecorder lockMetricsRecorder(MeterRegistry meterRegistry) {
        log.info("初始化 LockMetricsRecorder (Micrometer 指标)");
        return new LockMetricsRecorder(meterRegistry);
    }

    /**
     * 配置日志记录器
     *
     * @param properties Redisson 配置属性
     * @return LockLoggingRecorder
     */
    @Bean
    @ConditionalOnProperty(prefix = "patra.redisson.observability", name = "logging-enabled", havingValue = "true", matchIfMissing = true)
    public LockLoggingRecorder lockLoggingRecorder(RedissonProperties properties) {
        log.info("初始化 LockLoggingRecorder (日志记录)");
        return new LockLoggingRecorder(properties);
    }
}
