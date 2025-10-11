package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Exception that wraps persistence failures for plans and related entities.
 *
 * <p>Represents write/update/query failures for scheduling instances, plans, slices, tasks, and task attempts
 * when orchestrating ingestion plans. Common causes include database outages, network jitter, optimistic-lock
 * conflicts, or sequence generation issues.</p>
 * <ul>
 *   <li>Transient connection/timeout: retry a limited number of times.</li>
 *   <li>Optimistic-lock conflicts: use {@link #getStage()} to determine which entity needs rebuilding or a
 *   fresh read.</li>
 *   <li>Constraint violations: log and alert instead of retrying blindly.</li>
 * </ul>
 */
public class PlanPersistenceException extends IngestException implements HasErrorTraits {

    /**
     * Stage classification for persistence operations.
     * <p>Provides context to drive differentiated retry/compensation.</p>
     */
    public enum Stage {
        /** Failure while persisting scheduling instances. */
        SCHEDULE_INSTANCE,
        /** Failure while writing/updating the plan aggregate. */
        PLAN,
        /** Failure while persisting plan slices. */
        PLAN_SLICE,
        /** Failure while writing/updating tasks. */
        TASK,
        /** Failure while recording task retries/attempts. */
        TASK_RETRY
    }

    /** Stage where the failure occurred. */
    private final Stage stage;

    /**
     * Construct the exception with the stage and message.
     *
     * @param stage   failing stage
     * @param message descriptive message
     */
    public PlanPersistenceException(Stage stage, String message) {
        this(stage, message, null);
    }

    /**
     * Construct the exception with the stage, message, and underlying cause.
     *
     * @param stage   failing stage
     * @param message descriptive message
     * @param cause   underlying cause
     */
    public PlanPersistenceException(Stage stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    /**
     * Expose the stage of the failure.
     *
     * @return stage enumeration
     */
    public Stage getStage() {
        return stage;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
    }
}
