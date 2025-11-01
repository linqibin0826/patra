package com.patra.ingest.app.usecase.relay.config;

import com.patra.ingest.domain.policy.RelayRetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean registration for the Outbox Relay use case.
 *
 * <p>Responsibilities: expose the retry policy backed by configuration.
 *
 * <p>Note: Clock bean is provided by patra-spring-boot-starter-core and auto-injected where needed.
 */
@Configuration
public class OutboxRelayConfiguration {

  /** Build the retry policy: exponential backoff with an upper bound. */
  @Bean
  public RelayRetryPolicy relayRetryPolicy(OutboxRelayProperties properties) {
    return new RelayRetryPolicy(
        properties.getInitialBackoff(),
        properties.getBackoffMultiplier(),
        properties.getMaxBackoff());
  }
}
