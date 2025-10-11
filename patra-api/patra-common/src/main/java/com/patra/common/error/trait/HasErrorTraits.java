package com.patra.common.error.trait;

import java.util.Set;

/**
 * Marker interface for exceptions that expose semantic {@link ErrorTrait}s.
 *
 * <p>Enables the error-resolution pipeline to rely on semantics rather than
 * class-name heuristics when deriving HTTP status codes and response payloads.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface HasErrorTraits {
    
    /**
     * Returns the semantic traits associated with this exception.
     *
     * @return a set of traits; multiple traits may be provided to drive
     *     fine-grained classification.
     */
    Set<ErrorTrait> getErrorTraits();
}
