package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Base exception class for Registry "not found" semantics.
 *
 * <p>Represents requested resources (namespaces, catalogs, types, etc.) that do not exist
 * or are inaccessible due to business rules. All "not found" exceptions should extend this
 * class to ensure consistent error trait classification and handling.
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryNotFound extends RegistryException implements HasErrorTraits {

    /**
     * Creates an exception with a message.
     *
     * @param message detail message
     */
    protected RegistryNotFound(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and root cause.
     *
     * @param message detail message
     * @param cause root cause
     */
    protected RegistryNotFound(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns the error traits for this exception (always NOT_FOUND).
     *
     * @return set containing NOT_FOUND trait
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);
    }
}
