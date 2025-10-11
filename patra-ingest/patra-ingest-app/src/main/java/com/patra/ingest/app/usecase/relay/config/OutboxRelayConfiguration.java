package com.patra.ingest.app.usecase.relay.config;

import com.patra.ingest.domain.policy.RelayRetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Bean registration for the Outbox Relay use case.
 * <p>Responsibilities: expose the shared system clock and the retry policy backed by configuration.</p>
 */
@Configuration
public class OutboxRelayConfiguration {

    /** System UTC clock (overrideable in tests to inject a fixed clock). */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    /**
     * Build the retry policy: exponential backoff with an upper bound.
     */
    @Bean
    public RelayRetryPolicy relayRetryPolicy(OutboxRelayProperties properties) {
        return new RelayRetryPolicy(properties.getInitialBackoff(), properties.getBackoffMultiplier(), properties.getMaxBackoff());
    }
}
