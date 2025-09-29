package com.patra.ingest.app.relay.config;

import com.patra.ingest.domain.policy.RelayRetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Outbox Relay Bean 注册。
 */
@Configuration
public class OutboxRelayConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RelayRetryPolicy relayRetryPolicy(OutboxRelayProperties properties) {
        return new RelayRetryPolicy(properties.getInitialBackoff(), properties.getBackoffMultiplier(), properties.getMaxBackoff());
    }
}
