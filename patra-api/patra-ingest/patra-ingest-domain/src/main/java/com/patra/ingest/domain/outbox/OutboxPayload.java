package com.patra.ingest.domain.outbox;

/**
 * Marker interface for Outbox message payloads.
 * <p>Implementations should be immutable value objects (records or final classes)
 * that can be serialized to JSON.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Type Safety</b>: Enforces compile-time type checking for payload structure</li>
 *   <li><b>Immutability</b>: Payload objects should be immutable (use records or final fields)</li>
 *   <li><b>Serializability</b>: Must be serializable to JSON via Jackson</li>
 *   <li><b>Documentation</b>: Clear field documentation for downstream consumers</li>
 * </ul>
 *
 * <h3>Example Implementation</h3>
 * <pre>{@code
 * public record TaskPayload(
 *     Long taskId,
 *     Long planId,
 *     String provenance,
 *     String operation,
 *     String idempotentKey,
 *     Integer priority,
 *     Instant scheduledAt
 * ) implements OutboxPayload {
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxPayload {
    // Marker interface - no methods required
    // Serialization is handled by the framework via Jackson ObjectMapper
}
