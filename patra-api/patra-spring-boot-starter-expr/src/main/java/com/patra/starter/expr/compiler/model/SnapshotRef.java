package com.patra.starter.expr.compiler.model;

import java.time.Instant;
import java.util.Objects;

public record SnapshotRef(Long provenanceId, // TODO 这里不要使用provenanceId，统一用provenanceCode，可以删除这个字段
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
