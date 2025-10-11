package com.patra.ingest.app.usecase.relay.command;

import com.patra.common.messaging.ChannelKey;

import java.time.Duration;
import java.time.Instant;

/**
 * Relay instruction emitted by external schedulers; optional fields override defaults (blank values fall back to
 * configuration).
 * <p>Field notes:
 * <ul>
 *   <li>{@code channel}: target Outbox channel; {@code null} lets the plan choose the configured default or all channels.</li>
 *   <li>{@code triggeredAt}: trigger timestamp; {@code null} resolves to the current time.</li>
 *   <li>{@code batchSize}: maximum number of pending messages fetched in a batch; {@code null} or {@code <= 0} reverts to the default.</li>
 *   <li>{@code leaseDuration}: lease duration per message; {@code null} or non-positive falls back to the default.</li>
 *   <li>{@code maxAttempts}: maximum attempts (including the first run); {@code null} or {@code <= 0} uses the default.</li>
 *   <li>{@code initialBackoff}: initial delay before the first retry.</li>
 *   <li>{@code leaseOwner}: lease owner identifier; when blank the plan builder generates one (host + epoch millis + UUID).</li>
 * </ul>
 * Immutable contract consumed by {@link RelayPlan} construction.
 */
public record OutboxRelayCommand(
        ChannelKey channel,
        Instant triggeredAt,
        Integer batchSize,
        Duration leaseDuration,
        Integer maxAttempts,
        Duration initialBackoff,
        String leaseOwner
) { }
