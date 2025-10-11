package com.patra.ingest.app.usecase.relay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Default configuration for Outbox Relay (overridable from external config files).
 * <p>Property prefix: {@code patra.ingest.outbox-relay.*}</p>
 * <ul>
 *   <li>{@code enabled}: toggles the relay feature; when disabled schedulers return an empty report.</li>
 *   <li>{@code batchSize}: maximum number of messages fetched per batch; {@code <= 0} on the caller side falls back here.</li>
 *   <li>{@code leaseDuration}: lease duration per message; must cover fetch + publish + state persistence.</li>
 *   <li>{@code maxAttempts}: total allowed attempts including the first execution.</li>
 *   <li>{@code initialBackoff}: initial delay before the first retry.</li>
 *   <li>{@code backoffMultiplier}: exponential backoff multiplier; must be greater than {@code 1}.</li>
 *   <li>{@code maxBackoff}: upper bound for exponential backoff to avoid unbounded growth.</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "patra.ingest.outbox-relay")
public class OutboxRelayProperties {

    /** Whether the relay feature is enabled. */
    private boolean enabled = true;

    /** Default batch size. */
    private int batchSize = 200;
    /** Default lease duration. */
    private Duration leaseDuration = Duration.ofSeconds(30);
    /** Maximum attempts including the first try. */
    private int maxAttempts = 5;
    /** Initial backoff duration. */
    private Duration initialBackoff = Duration.ofSeconds(5);
    /** Backoff multiplier. */
    private double backoffMultiplier = 2.0d;
    /** Maximum backoff duration. */
    private Duration maxBackoff = Duration.ofMinutes(2);

}
