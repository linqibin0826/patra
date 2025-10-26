package com.patra.common.objectstorage;

/**
 * Strategy interface for generating standardized object storage keys.
 *
 * <p>Implementations define specific key generation patterns (e.g., date-partitioned, hierarchical,
 * custom) while ensuring consistent structure across all Papertrace microservices.
 *
 * <p><b>Design Pattern</b>: Strategy Pattern — allows different key generation strategies to be
 * swapped at runtime or configured per service/use case.
 *
 * <p><b>Thread Safety</b>: Implementations should be stateless and thread-safe to support singleton
 * usage across the application.
 *
 * <p><b>Standard Implementations</b>:
 *
 * <ul>
 *   <li>{@link DatePartitionedKeyGenerator} — Daily partitioned keys ({@code
 *       service/type/yyyy/MM/dd/id.ext})
 *   <li>Future: {@code MonthPartitionedKeyGenerator}, {@code HierarchicalKeyGenerator}, etc.
 * </ul>
 *
 * @author linqibin
 * @see ObjectKeyContext
 * @see DatePartitionedKeyGenerator
 * @since 0.1.0
 */
@FunctionalInterface
public interface ObjectKeyGenerator {

  /**
   * Generates an object storage key from the provided context.
   *
   * <p>The returned key should be a relative path (without bucket name) that uniquely identifies
   * the object within the storage bucket.
   *
   * <p><b>Normalization Requirements</b>:
   *
   * <ul>
   *   <li>Service names should be lowercase
   *   <li>Business types should follow kebab-case convention
   *   <li>Date segments should use consistent formatting (yyyy/MM/dd)
   *   <li>Path separators should use forward slashes ({@code /})
   * </ul>
   *
   * @param context immutable context containing all key generation parameters
   * @return generated object key path (e.g., {@code
   *     ingest/literature-batch/2025/10/26/pubmed-123.json})
   * @throws IllegalArgumentException if context contains invalid data
   */
  String generate(ObjectKeyContext context);
}
