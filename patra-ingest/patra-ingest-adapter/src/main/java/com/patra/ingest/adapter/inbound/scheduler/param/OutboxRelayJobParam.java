package com.patra.ingest.adapter.inbound.scheduler.param;

/**
 * Outbox Relay job parameters (passed via XXL-Job JSON).
 * <p>All fields are optional; null values fall back to defaults in the business layer:</p>
 * <ul>
 *   <li>channel: message channel; blank uses configured default</li>
 *   <li>batchSize: number of records to fetch per attempt; blank uses configured default</li>
 *   <li>leaseDuration: lease duration; supports ISO-8601 (e.g., PT15S) or plain seconds; blank uses default</li>
 *   <li>maxAttempts: maximum attempts (including the first); blank uses default (typically >= 3)</li>
 *   <li>initialBackoff: initial backoff duration; same formats as leaseDuration</li>
 * </ul>
 */
public record OutboxRelayJobParam(
        String channel,
        Integer batchSize,
        String leaseDuration,
        Integer maxAttempts,
        String initialBackoff
) { }
