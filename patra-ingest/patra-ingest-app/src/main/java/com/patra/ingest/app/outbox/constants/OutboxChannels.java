package com.patra.ingest.app.outbox.constants;

/**
 * Outbox channel constants.
 * <p>Defines messaging channels used in the Outbox framework for:</p>
 * <ul>
 *   <li>Message routing and topic mapping</li>
 *   <li>Deduplication key scoping (channel + dedupKey uniqueness)</li>
 *   <li>Channel-based filtering and monitoring</li>
 * </ul>
 *
 * <h3>Naming Convention</h3>
 * <p>Channels follow hierarchical underscore structure: {@code MODULE_SEMANTIC_STATE}</p>
 * <ul>
 *   <li><b>MODULE</b>: Service module name (e.g., INGEST, REGISTRY)</li>
 *   <li><b>SEMANTIC</b>: Business semantic (e.g., TASK, PLAN, LITERATURE)</li>
 *   <li><b>STATE</b>: State or action (e.g., READY, CREATED, UPDATED)</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Override
 * protected String getChannel() {
 *     return OutboxChannels.INGEST_TASK_READY;
 * }
 * }</pre>
 *
 * <h3>Design Considerations</h3>
 * <ul>
 *   <li>Channel names map to MQ topics/exchanges for routing</li>
 *   <li>Channels provide deduplication scope (unique constraint: channel + dedupKey)</li>
 *   <li>Channel-based metrics enable fine-grained monitoring</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class OutboxChannels {

    /**
     * Ingest task ready channel.
     * <p>Used when scheduler generates tasks that are queued and ready for execution.</p>
     * <p><b>Business Flow</b>: Schedule trigger → Task creation → Task queue → Task execution</p>
     * <p><b>Consumers</b>: Task execution workers</p>
     */
    public static final String INGEST_TASK_READY = "INGEST_TASK_READY";

    /**
     * Ingest plan created channel.
     * <p>Used when a new ingestion plan is created.</p>
     * <p><b>Business Flow</b>: Plan creation → Event publishing → Downstream processing</p>
     * <p><b>Consumers</b>: Audit service, monitoring dashboard</p>
     */
    public static final String INGEST_PLAN_CREATED = "INGEST_PLAN_CREATED";

    /**
     * Ingest plan updated channel.
     * <p>Used when an ingestion plan state transitions or configuration changes.</p>
     * <p><b>Business Flow</b>: Plan state change → Event publishing → Downstream sync</p>
     * <p><b>Consumers</b>: Audit service, monitoring dashboard</p>
     */
    public static final String INGEST_PLAN_UPDATED = "INGEST_PLAN_UPDATED";

    private OutboxChannels() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
