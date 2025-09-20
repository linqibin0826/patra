package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Semantic base exception for Registry business rule violations.
 * This exception indicates that an operation violates business rules, validation constraints,
 * or data integrity requirements (e.g., invalid schema format, constraint violations).
 * 
 * All Registry rule violation exceptions should extend this class to ensure consistent
 * error trait classification and handling.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryRuleViolation extends RegistryException implements HasErrorTraits {
    
    /**
     * Constructs a new registry rule violation exception with the specified detail message.
     * 
     * @param message the detail message explaining the rule violation
     */
    protected RegistryRuleViolation(String message) {
        super(message);
    }
    
    /**
     * Constructs a new registry rule violation exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining the rule violation
     * @param cause the cause of this exception
     */
    protected RegistryRuleViolation(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Returns the error traits for rule violation exceptions.
     * All Registry rule violation exceptions are classified with the RULE_VIOLATION trait.
     * 
     * @return set containing the RULE_VIOLATION error trait
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.RULE_VIOLATION);
    }
}