package com.patra.ingest.app.outbox.constants;

/**
 * Outbox aggregate type enum.
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
 *     return OutboxAggregateTypes.TASK.getCode();
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
public enum OutboxAggregateTypes {

    /**
     * Task aggregate type.
     * <p>Used for task queue events (task creation, scheduling, execution).</p>
     */
    TASK("Task", "Task aggregate - for task queue events"),

    /**
     * Plan aggregate type.
     * <p>Used for ingestion plan lifecycle events (plan creation, state transitions).</p>
     */
    PLAN("Plan", "Plan aggregate - for ingestion plan lifecycle events"),

    /**
     * Literature data aggregate type.
     * <p>Used for literature data processing events (parsing, cleansing, storage).</p>
     */
    LITERATURE_DATA("LiteratureData", "Literature data aggregate - for data processing events");

    private final String code;
    private final String description;

    OutboxAggregateTypes(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns the aggregate type code.
     * <p>This value is stored in {@code ing_outbox_message.aggregate_type} field.</p>
     *
     * @return Aggregate type code (e.g., "Task", "Plan", "LiteratureData")
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the human-readable description.
     *
     * @return Description of this aggregate type
     */
    public String getDescription() {
        return description;
    }

    /**
     * Finds enum by code.
     *
     * @param code Aggregate type code
     * @return Matching enum value
     * @throws IllegalArgumentException if code is not found
     */
    public static OutboxAggregateTypes fromCode(String code) {
        for (OutboxAggregateTypes type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown aggregate type code: " + code);
    }
}
