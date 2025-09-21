package com.patra.starter.expr.compiler.model;

public record CompileOptions(
        boolean strict,
        int maxQueryLength,
        String timezone,
        boolean traceEnabled
) {
    public static CompileOptions defaults() {
        return new CompileOptions(true, 0, "UTC", false);
    }

    public CompileOptions withStrict(boolean value) {
        return new CompileOptions(value, maxQueryLength, timezone, traceEnabled);
    }

    public CompileOptions withMaxQueryLength(int value) {
        return new CompileOptions(strict, value, timezone, traceEnabled);
    }

    public CompileOptions withTimezone(String value) {
        return new CompileOptions(strict, maxQueryLength, value, traceEnabled);
    }

    public CompileOptions withTraceEnabled(boolean value) {
        return new CompileOptions(strict, maxQueryLength, timezone, value);
    }
}
