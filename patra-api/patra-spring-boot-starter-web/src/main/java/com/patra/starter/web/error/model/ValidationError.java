package com.patra.starter.web.error.model;

/**
 * Immutable representation of a validation error entry exposed through ProblemDetail extensions.
 * Sensitive values are pre-masked to avoid leaking confidential data.
 *
 * @param field         logical field name
 * @param rejectedValue sanitized rejected value, when available
 * @param message       human-readable validation message
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ValidationError(
    String field,
    Object rejectedValue,
    String message
) {}
