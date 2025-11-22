package com.patra.starter.expr.compiler.model;

/// Common operation type constants that help callers avoid hardcoded strings.
/// 
/// Operation types differentiate configuration slices for the same provenance in different
/// business scenarios.
/// 
/// **Usage example:**
/// 
/// ```java
/// CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
///     .forOperationType(OperationTypes.UPDATE)
///     .build();
/// ```
public final class OperationTypes {

  /// Initial full harvest, typically used for the first crawl or rebuilding large windows.
  public static final String HARVEST = "HARVEST";

  /// Historical backfill or remediation runs.
  public static final String BACKFILL = "BACKFILL";

  /// Incremental updates used for regularly syncing new or changed data.
/// 
/// This is the most common operation type and often uses aggressive parameters to capture fresh
/// content.
  public static final String UPDATE = "UPDATE";

  /// Full synchronization covering the complete dataset.
/// 
/// Often used for initialization or periodic refreshes that require special handling for large
/// data volumes.
  public static final String FULL = "FULL";

  /// Interactive search scenarios.
/// 
/// Usually optimized for latency and may rely on different timeout or retry policies.
  public static final String SEARCH = "SEARCH";

  /// Metric or analytical aggregations.
  public static final String METRICS = "METRICS";

  /// Monitoring or health-check operations with lightweight queries.
  public static final String MONITOR = "MONITOR";

  /// Data validation or quality assurance flows.
  public static final String VALIDATE = "VALIDATE";

  /// Analytical operations that support complex investigation or reporting use cases.
  public static final String ANALYZE = "ANALYZE";

  /// Bulk data export.
  public static final String EXPORT = "EXPORT";

  private OperationTypes() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
