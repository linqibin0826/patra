package com.patra.starter.expr.compiler.model;

import java.time.Instant;
import java.util.Objects;

public record SnapshotRef(Long provenanceId,
                          String provenanceCode,
                          String operationCode,
                          long version,
                          Instant capturedAt) {
    public SnapshotRef {
        Objects.requireNonNull(provenanceCode, "provenanceCode");
        Objects.requireNonNull(operationCode, "operationCode");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }
}
