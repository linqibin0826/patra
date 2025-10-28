package com.patra.common.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Date-partitioned object key generator following the pattern: {@code
 * {service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}}
 *
 * <p>This implementation creates object keys with daily time partitioning, enabling efficient
 * lifecycle management, cost analysis, and query optimization based on date ranges.
 *
 * <p><b>Pattern Structure</b>:
 *
 * <ul>
 *   <li><b>Service</b>: Microservice name (normalized to lowercase)
 *   <li><b>Business Type</b>: Business category (normalized to kebab-case)
 *   <li><b>Date Partition</b>: Three-level hierarchy (yyyy/MM/dd)
 *   <li><b>Business ID</b>: Unique identifier (preserved as-is)
 *   <li><b>Extension</b>: File extension including compound types (e.g., json.gz)
 * </ul>
 *
 * <p><b>Example Output</b>:
 *
 * <pre>{@code
 * ingest/literature-batch/2025/10/26/pubmed-123-batch-001.json
 * storage/metadata-snapshot/2025/10/25/snapshot-20251025-001.json.gz
 * catalog/literature-index/2025/10/26/index-pmid-12345.xml
 * }</pre>
 *
 * <p><b>Normalization Rules</b>:
 *
 * <ul>
 *   <li>Service name → lowercase
 *   <li>Business type → lowercase with underscores converted to hyphens (kebab-case)
 *   <li>Extension → leading dot removed if present
 * </ul>
 *
 * <p><b>Thread Safety</b>: This class is stateless and thread-safe. The singleton instance {@link
 * #INSTANCE} can be safely shared across the application.
 *
 * @author linqibin
 * @see ObjectKeyGenerator
 * @see ObjectKeyContext
 * @since 0.1.0
 */
public final class DatePartitionedKeyGenerator implements ObjectKeyGenerator {

  /** Singleton instance for shared usage across the application. */
  public static final DatePartitionedKeyGenerator INSTANCE = new DatePartitionedKeyGenerator();

  private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
  private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd");

  /** Private constructor to enforce singleton pattern. */
  private DatePartitionedKeyGenerator() {}

  /**
   * Generates a date-partitioned object key from the provided context.
   *
   * <p>The generated key follows the pattern: {@code
   * {service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}}
   *
   * @param context immutable context containing all key generation parameters
   * @return generated object key path
   * @throws IllegalArgumentException if context contains invalid data
   */
  @Override
  public String generate(ObjectKeyContext context) {
    String normalizedService = normalizeServiceName(context.serviceName());
    String normalizedBusinessType = normalizeBusinessType(context.businessType());
    String datePartition = buildDatePartition(context.partitionDate());
    String normalizedExtension = normalizeExtension(context.extension());

    return String.format(
        "%s/%s/%s/%s.%s",
        normalizedService,
        normalizedBusinessType,
        datePartition,
        context.businessId(),
        normalizedExtension);
  }

  /**
   * Normalizes service name to lowercase.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"Ingest" → "ingest"
   *   <li>"STORAGE" → "storage"
   *   <li>"patra-catalog" → "patra-catalog"
   * </ul>
   */
  private String normalizeServiceName(String serviceName) {
    return serviceName.toLowerCase(Locale.ROOT);
  }

  /**
   * Normalizes business type to kebab-case.
   *
   * <p>Converts underscores to hyphens and lowercases the string for consistency.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"literature_batch" → "literature-batch"
   *   <li>"LiteratureBatch" → "literaturebatch"
   *   <li>"metadata-snapshot" → "metadata-snapshot"
   * </ul>
   */
  private String normalizeBusinessType(String businessType) {
    return businessType.toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /**
   * Builds the date partition path in yyyy/MM/dd format.
   *
   * <p>Example: {@code 2025-10-26} → {@code 2025/10/26}
   */
  private String buildDatePartition(LocalDate partitionDate) {
    String year = YEAR_FORMATTER.format(partitionDate);
    String month = MONTH_FORMATTER.format(partitionDate);
    String day = DAY_FORMATTER.format(partitionDate);
    return String.format("%s/%s/%s", year, month, day);
  }

  /**
   * Normalizes file extension by removing leading dot if present.
   *
   * <p>Supports compound extensions like "json.gz".
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>".json" → "json"
   *   <li>"json" → "json"
   *   <li>".json.gz" → "json.gz"
   *   <li>"tar.gz" → "tar.gz"
   * </ul>
   */
  private String normalizeExtension(String extension) {
    return extension.startsWith(".") ? extension.substring(1) : extension;
  }
}
