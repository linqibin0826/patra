package com.patra.starter.expr.compiler.model;

/// Expression compiler commonly used endpoint name constants.
///
/// Endpoint names are used to identify different API operation types, and the system selects
/// corresponding endpoint configuration and rendering rules based on the endpoint name.
///
/// **Usage example:**
///
/// ```java
/// // Use constants instead of hardcoded strings
/// CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
///     .forOperation(EndpointNames.SEARCH)
///     .build();
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class EndpointNames {

  /// Search operation: used to retrieve a list of records matching criteria.
  ///
  /// This is the most commonly used operation type, typically returning an ID list or paginated
  /// results.
  public static final String SEARCH = "SEARCH";

  /// Detail operation: used to retrieve complete information for a specific record.
  ///
  /// Usually requires providing a record ID and returns detailed metadata information.
  public static final String DETAIL = "DETAIL";

  /// List operation: used to retrieve a simplified list of records.
  ///
  /// Similar to SEARCH, but may return fewer fields or a different format.
  public static final String LIST = "LIST";

  /// Count operation: used to retrieve the count of records matching criteria.
  ///
  /// Only returns count information, not actual record data.
  public static final String COUNT = "COUNT";

  /// Fetch operation: generic data retrieval operation.
  ///
  /// Some APIs use FETCH instead of DETAIL to represent detail retrieval.
  public static final String FETCH = "FETCH";

  /// Query operation: similar to search but semantically more oriented towards complex queries.
  public static final String QUERY = "QUERY";

  /// Export operation: used for bulk data export.
  public static final String EXPORT = "EXPORT";

  // Prevent instantiation
  private EndpointNames() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
