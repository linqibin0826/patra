package com.patra.ingest.domain.outbox;

/**
 * Marker interface for Outbox message headers.
 * <p>Headers contain metadata for tracing, routing, and debugging purposes.
 * Implementations should be immutable value objects (records or final classes).</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Type Safety</b>: Enforces compile-time type checking for headers structure</li>
 *   <li><b>Immutability</b>: Header objects should be immutable (use records or final fields)</li>
 *   <li><b>Serializability</b>: Must be serializable to JSON via Jackson</li>
 *   <li><b>Observability</b>: Should include tracing and correlation identifiers</li>
 * </ul>
 *
 * <h3>Common Header Fields</h3>
 * <ul>
 *   <li><b>Tracing</b>: scheduleInstanceId, traceId, correlationId</li>
 *   <li><b>Timing</b>: triggeredAt, occurredAt, publishedAt</li>
 *   <li><b>Source</b>: scheduler, schedulerJobId, sourceSystem</li>
 *   <li><b>Metadata</b>: version, eventType, causationId</li>
 * </ul>
 *
 * <h3>Example Implementation</h3>
 * <pre>{@code
 * public record TaskHeaders(
 *     Long scheduleInstanceId,
 *     String scheduler,
 *     String schedulerJobId,
 *     Instant triggeredAt,
 *     Instant occurredAt
 * ) implements OutboxHeaders {
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxHeaders {
    // Marker interface - no methods required
    // Serialization is handled by the framework via Jackson ObjectMapper
}
