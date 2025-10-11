package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Exception thrown when pre-assembly validation fails for a plan.
 *
 * <p>Raised during the pre-flight validation chain executed before slicing or assembly (window validity,
 * boundary checks, queue backpressure, capability alignment, and so on). Once thrown, the request does not meet
 * business prerequisites and should not be retried. Adjust scheduler parameters, time windows, or system
 * configuration before resubmitting.</p>
 *
 * <p>Handling guidelines:
 * <ul>
 *   <li>Application layer: log at INFO/WARN depending on expectations, return a readable message upstream, and
 *   do not retry.</li>
 *   <li>Monitoring: aggregate {@link Reason} to uncover scheduling or configuration gaps.</li>
 *   <li>Diagnosis: correlate with window start/end, plan key, and backpressure metrics.</li>
 * </ul>
 * </p>
 */
public class PlanValidationException extends IngestException implements HasErrorTraits {

    /**
     * Reason categories for validation failure.
     * <p>All values represent caller-correctable issues; automatic retries are not appropriate.</p>
     */
    public enum Reason {
        /** Scheduler did not provide a window. */
        WINDOW_MISSING,
        /** Invalid window boundaries (start >= end, misaligned, or out of range). */
        WINDOW_INVALID,
        /** Window span exceeds the allowed maximum (needs splitting or reduction). */
        WINDOW_TOO_LARGE,
        /** Window span is too small to generate efficient slices. */
        WINDOW_TOO_SMALL,
        /** Downstream queues are back-pressured; reject new plans for now. */
        QUEUE_BACKPRESSURE,
        /** Capability/provenance/endpoint combination is unsupported by the platform. */
        CAPABILITY_MISMATCH
    }

    /** Specific reason for the failure; {@code null} when not distinguished. */
    private final Reason reason;

    /**
     * Create the exception with a message and no explicit reason.
     *
     * @param message human-readable failure description
     */
    public PlanValidationException(String message) {
        this(message, null, null);
    }

    /**
     * Create the exception with a message and reason.
     *
     * @param message failure description
     * @param reason  failure reason
     */
    public PlanValidationException(String message, Reason reason) {
        this(message, reason, null);
    }

    /**
     * Create the exception with a message, reason, and underlying cause.
     *
     * @param message failure description
     * @param reason  failure reason
     * @param cause   underlying exception (optional)
     */
    public PlanValidationException(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Create the exception with a message and underlying cause without specifying the reason.
     *
     * @param message failure description
     * @param cause   underlying exception
     */
    public PlanValidationException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * Expose the validation failure reason.
     *
     * @return failure reason, possibly {@code null}
     */
    public Reason getReason() {
        return reason;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.RULE_VIOLATION);
    }
}
