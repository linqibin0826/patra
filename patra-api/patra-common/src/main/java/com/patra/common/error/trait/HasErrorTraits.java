package com.patra.common.error.trait;

import java.util.Set;

/**
 * Interface for exceptions that can be semantically classified using ErrorTraits.
 * This enables the error resolution algorithm to map exceptions to appropriate
 * HTTP status codes and error responses based on their semantic meaning rather
 * than just their class names.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface HasErrorTraits {
    
    /**
     * Returns the set of error traits that classify this exception's semantic meaning.
     * Multiple traits can be applied to a single exception to provide rich
     * classification for error handling.
     * 
     * @return set of error traits, must not be null (can be empty)
     */
    Set<ErrorTrait> getErrorTraits();
}