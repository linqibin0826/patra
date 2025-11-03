package com.patra.starter.expr.compiler.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 编译期间使用的 Registry 快照的轻量级引用。
 *
 * @param provenanceId 溯源ID
 * @param provenanceCode 溯源代码
 * @param endpointName 端点名称
 * @param version 快照版本
 * @param capturedAt 快照捕获时间
 * @author linqibin
 * @since 0.1.0
 */
public record SnapshotRef(
    Long provenanceId,
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
