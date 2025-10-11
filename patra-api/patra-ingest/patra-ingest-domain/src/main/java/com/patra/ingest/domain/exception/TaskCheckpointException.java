package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Exception thrown when task checkpoint serialization or deserialization fails.
 *
 * <p>Raised whenever checkpoint persistence or recovery fails due to JSON/binary conversion issues,
 * missing fields, or incompatible versions.</p>
 * <p>Handling guidance:
 * <ul>
 *   <li>{@code PARSE}: consider restarting from the beginning or halting the task for manual intervention
 *   depending on idempotency guarantees.</li>
 *   <li>{@code SERIALIZE}: retry with a limit; persistent failures should alert operators so progress is not lost.</li>
 * </ul>
 * </p>
 */
public class TaskCheckpointException extends IngestException implements HasErrorTraits {

    public enum Type {
        /** Failed to parse an existing checkpoint. */
        PARSE,
        /** Failed to serialize a new checkpoint. */
        SERIALIZE
    }

    /** Type of failure that occurred. */
    private final Type type;

    /**
     * Create the exception.
     *
     * @param type    failure type
     * @param message descriptive message
     * @param cause   underlying exception
     */
    public TaskCheckpointException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    /**
     * Expose the failure type.
     *
     * @return type enumeration
     */
    public Type getType() {
        return type;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.RULE_VIOLATION);
    }
}
