package com.patra.ingest.adapter.scheduler.param;

/**
 * Outbox Relay job parameters (passed via XXL-Job JSON).
 *
 * <p>All fields are optional; null values fall back to defaults in the business layer.
 *
 * @param channel message channel; blank uses configured default
 * @param batchSize number of records to fetch per attempt; blank uses configured default
 * @param leaseDuration lease duration; supports ISO-8601 (e.g., PT15S) or plain seconds; blank uses
 *     default
 * @param maxAttempts maximum attempts (including the first); blank uses default (typically >= 3)
 * @param initialBackoff initial backoff duration; same formats as leaseDuration
 */
public record OutboxRelayJobParam(
    String channel,
    Integer batchSize,
    String leaseDuration,
    Integer maxAttempts,
    String initialBackoff) {}
