package com.patra.ingest.app.outbox.constants;

/**
 * Outbox operation type constants.
 * <p>Defines operation types used in the Outbox framework for:</p>
 * <ul>
 *   <li>Message metadata and routing</li>
 *   <li>Event type identification</li>
 *   <li>Metrics tagging and monitoring</li>
 * </ul>
 *
 * <h3>Operation Type Categories</h3>
 * <ul>
 *   <li><b>Business Operations</b>: Domain-specific operation types (e.g., TASK_READY, CREATED)</li>
 *   <li><b>Framework Operations</b>: Internal framework operations (e.g., BATCH, RETRY)</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Business operation type
 * @Override
 * protected String getOperationType(TaskQueuedEvent event) {
 *     return OutboxOperationTypes.TASK_READY;
 * }
 *
 * // Framework operation type (for metrics)
 * metrics.recordPublish(aggregateType, OutboxOperationTypes.BATCH, true, duration);
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class OutboxOperationTypes {

    // ==================== Business Operation Types ====================

    /**
     * Task ready operation.
     * <p>Indicates a task has been queued and is ready for execution.</p>
     */
    public static final String TASK_READY = "TASK_READY";

    /**
     * Entity created operation.
     * <p>Indicates a new entity has been created.</p>
     */
    public static final String CREATED = "CREATED";

    /**
     * Entity updated operation.
     * <p>Indicates an existing entity has been updated.</p>
     */
    public static final String UPDATED = "UPDATED";

    /**
     * Entity deleted operation.
     * <p>Indicates an entity has been deleted.</p>
     */
    public static final String DELETED = "DELETED";

    // ==================== Framework Operation Types ====================

    /**
     * Batch publish operation.
     * <p>Used for metrics tagging when publishing messages in batch mode.</p>
     */
    public static final String BATCH = "batch";

    /**
     * Retry publish operation.
     * <p>Used for metrics tagging when retry-publishing messages via UPSERT.</p>
     */
    public static final String RETRY = "retry";

    private OutboxOperationTypes() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
