package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Exception raised when the outbox state cannot be persisted.
 *
 * <p>Wraps failures that occur while transitioning outbox messages between {@code PUBLISHED}, retry, and
 * dead-letter states. Typical root causes include concurrent updates (optimistic locking updating zero rows),
 * temporary database unavailability, or sequence generation failures.</p>
 * <p>Recovery guidance:
 * <ul>
 *   <li>Concurrency conflicts: retry with exponential backoff keyed by the retry count.</li>
 *   <li>Connection/timeouts: classify as retryable so the scheduler can reattempt.</li>
 *   <li>Sustained failures (beyond thresholds): trigger alerts and investigate database health or table locks.</li>
 * </ul>
 * </p>
 */
public class OutboxPersistenceException extends IngestException implements HasErrorTraits {

    public enum Stage {
        /** Failure while marking a message as published. */
        MARK_PUBLISHED,
        /** Failure while marking a message for retry (often due to concurrent writes). */
        MARK_RETRY,
        /** Failure while marking a message as dead-lettered. */
        MARK_DEAD
    }

    /** Stage at which persistence failed. */
    private final Stage stage;

    /**
     * Create the exception with the failure stage and message.
     *
     * @param stage   stage that failed
     * @param message descriptive message
     */
    public OutboxPersistenceException(Stage stage, String message) {
        super(message);
        this.stage = stage;
    }

    /**
     * Create the exception with the failure stage, message, and underlying cause.
     *
     * @param stage   stage that failed
     * @param message descriptive message
     * @param cause   underlying cause
     */
    public OutboxPersistenceException(Stage stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    /**
     * Expose the stage where persistence failed.
     *
     * @return stage enumeration
     */
    public Stage getStage() {
        return stage;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.CONFLICT);
    }
}
