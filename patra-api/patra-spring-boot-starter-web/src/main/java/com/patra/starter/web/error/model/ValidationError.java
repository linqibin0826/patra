package com.patra.starter.web.error.model;

/**
 * Represents a validation error with field information and masked sensitive data.
 * Used in ProblemDetail responses to provide detailed validation error information.
 * 
 * @param field the field name that failed validation, must not be null
 * @param rejectedValue the rejected value (may be masked for sensitive fields), can be null
 * @param message the validation error message, must not be null
 * 
 * @author linqibin
 * @since 0.1.0
 */
public record ValidationError(
    String field,
    Object rejectedValue,
    String message
) {}