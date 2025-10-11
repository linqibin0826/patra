package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Exception raised during execution of the outbox relay pipeline.
 *
 * <p>Represents non-persistence failures in the flow of fetching messages, acquiring leases, publishing to the
 * broker, or updating state (for example network interruptions, third-party SDK errors, serialization issues).
 * Compared with {@link OutboxPersistenceException}, this class emphasizes external dependency or publishing
 * failures rather than database updates.</p>
 * <p>Handling strategy:
 * <ul>
 *   <li>Recoverable (e.g., transient network issues): mark for retry.</li>
 *   <li>Non-recoverable (unsupported format, target rejection): route to dead letter according to policy.</li>
 * </ul>
 * </p>
 */
public class OutboxRelayExecutionException extends IngestException implements HasErrorTraits {

    /**
     * Construct the exception with a message and underlying cause.
     *
     * @param message descriptive message
     * @param cause   underlying exception
     */
    public OutboxRelayExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
    }
}
