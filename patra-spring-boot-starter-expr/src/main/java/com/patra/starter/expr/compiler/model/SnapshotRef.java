package com.patra.starter.expr.compiler.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight reference to the Registry snapshot used during compilation.
 */
public record SnapshotRef(Long provenanceId,
                          String provenanceCode,
                          String endpointName,
                          long version,
                          Instant capturedAt) {
    public SnapshotRef {
        Objects.requireNonNull(provenanceCode, "provenanceCode");
        Objects.requireNonNull(endpointName, "endpointName");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }
}
