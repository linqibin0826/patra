package com.patra.ingest.app.outbox.constants;

/**
 * Outbox aggregate type constants.
 * <p>Defines all valid aggregate types used in the Outbox framework for:</p>
 * <ul>
 *   <li>Micrometer metrics tag cardinality control</li>
 *   <li>Database partitioning and indexing strategies</li>
 *   <li>Message routing and filtering</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Override
 * protected String getAggregateType() {
 *     return OutboxAggregateTypes.TASK;
 * }
 * }</pre>
 *
 * <h3>Configuration Reference</h3>
 * <p>These values must match the allowed aggregate types in Nacos configuration:</p>
 * <pre>
 * papertrace:
 *   outbox:
 *     publisher:
 *       allowed-aggregate-types:
 *         - Task
 *         - Plan
 *         - LiteratureData
 * </pre>
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.ingest.app.outbox.config.OutboxPublisherProperties#getAllowedAggregateTypes()
 */
public final class OutboxAggregateTypes {

    /**
     * Task aggregate type.
     * <p>Used for task queue events (task creation, scheduling, execution).</p>
     */
    public static final String TASK = "Task";

    /**
     * Plan aggregate type.
     * <p>Used for ingestion plan lifecycle events (plan creation, state transitions).</p>
     */
    public static final String PLAN = "Plan";

    /**
     * Literature data aggregate type.
     * <p>Used for literature data processing events (parsing, cleansing, storage).</p>
     */
    public static final String LITERATURE_DATA = "LiteratureData";

    private OutboxAggregateTypes() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
