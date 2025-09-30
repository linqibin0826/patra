package com.patra.ingest.app.usecase.relay.config;

import com.patra.ingest.domain.policy.RelayRetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Outbox Relay Bean 注册配置。
 * <p>职责：提供统一注入的系统时钟与基于配置的重试策略。</p>
 */
@Configuration
public class OutboxRelayConfiguration {

    /** 系统 UTC 时钟（可测试时通过覆盖 Bean 注入固定时钟）。 */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    /**
     * 构建重试策略：指数退避 + 上限控制。
     */
    @Bean
    public RelayRetryPolicy relayRetryPolicy(OutboxRelayProperties properties) {
        return new RelayRetryPolicy(properties.getInitialBackoff(), properties.getBackoffMultiplier(), properties.getMaxBackoff());
    }
}
