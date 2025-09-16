// src/main/java/com/example/ingest/app/command/SafetyLimits.java
package com.patra.ingest.app.usecase.command;

import java.time.Duration;
import java.util.Optional;

/** 运行安全阈值，避免误采或过载。 */
public record SafetyLimits(
        Optional<Integer> maxPages,
        Optional<Integer> maxRecords,
        Optional<Duration> maxRuntime
) {
    public SafetyLimits {
        maxPages = maxPages == null ? Optional.empty() : maxPages;
        maxRecords = maxRecords == null ? Optional.empty() : maxRecords;
        maxRuntime = maxRuntime == null ? Optional.empty() : maxRuntime;

        maxPages.ifPresent(v -> { if (v <= 0) throw new IllegalArgumentException("maxPages 必须为正"); });
        maxRecords.ifPresent(v -> { if (v <= 0) throw new IllegalArgumentException("maxRecords 必须为正"); });
        maxRuntime.ifPresent(v -> { if (v.isZero() || v.isNegative()) throw new IllegalArgumentException("maxRuntime 必须为正"); });
    }

    public static SafetyLimits empty() {
        return new SafetyLimits(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
