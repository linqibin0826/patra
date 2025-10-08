package com.patra.ingest.app.usecase.relay.support;

/**
 * Outbox channel constants.
 * <p><b>DEPRECATED</b>: This class has been moved to {@code com.patra.ingest.app.outbox.constants.OutboxChannels}.
 * Please update your imports to use the new location.</p>
 * <p>This class will be removed in a future version.</p>
 *
 * @author linqibin
 * @since 0.1.0
 * @deprecated Use {@link com.patra.ingest.app.outbox.constants.OutboxChannels} instead
 */
@Deprecated(since = "0.1.0", forRemoval = true)
public final class OutboxChannels {

    /**
     * Ingest task ready channel.
     * @deprecated Use {@link com.patra.ingest.app.outbox.constants.OutboxChannels#INGEST_TASK_READY} instead
     */
    @Deprecated(since = "0.1.0", forRemoval = true)
    public static final String INGEST_TASK_READY = com.patra.ingest.app.outbox.constants.OutboxChannels.INGEST_TASK_READY;

    private OutboxChannels() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
