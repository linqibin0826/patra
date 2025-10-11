package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;

import java.util.EnumSet;
import java.util.Set;

/**
 * Exception wrapper for failures during outbox publishing.
 * <p>Includes a {@link Reason} so callers can classify retryable versus non-retryable scenarios.</p>
 */
public class OutboxPublishException extends OutboxRelayExecutionException {

    private final Reason reason;

    public OutboxPublishException(Reason reason, String message) {
        super(message, null);
        this.reason = reason;
    }

    public OutboxPublishException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        if (reason == Reason.CHANNEL_NOT_ALLOWED || reason == Reason.HEADERS_INVALID) {
            return EnumSet.of(ErrorTrait.RULE_VIOLATION);
        }
        return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
    }

    /** Root cause classification for publishing failures. */
    public enum Reason {
        CHANNEL_NOT_ALLOWED(true),
        HEADERS_INVALID(true),
        SEND_FAILED(false);

        private final boolean fatal;

        Reason(boolean fatal) {
            this.fatal = fatal;
        }

        public boolean isFatal() {
            return fatal;
        }
    }
}
