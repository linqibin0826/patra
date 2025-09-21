package com.patra.expr;

/**
 * Represents whether a text-based comparison is case-sensitive.
 */
public enum CaseSensitivity {
    INSENSITIVE,
    SENSITIVE;

    public static CaseSensitivity of(boolean caseSensitive) {
        return caseSensitive ? SENSITIVE : INSENSITIVE;
    }

    public boolean isSensitive() {
        return this == SENSITIVE;
    }
}
