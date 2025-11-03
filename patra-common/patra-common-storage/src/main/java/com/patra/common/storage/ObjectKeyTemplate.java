package com.patra.common.storage;

import java.time.LocalDate;

/**
 * 提供便捷工厂方法的静态工具类,用于生成标准化对象存储键。
 *
 * <p>此类充当 {@link ObjectKeyGenerator} 策略实现的门面, 为常见用例提供简化的 API,无需直接实例化生成器。
 *
 * <p><b>主要用例</b>:
 *
 * <ul>
 *   <li>使用默认(每日)分区快速生成键
 *   <li>当前日期分区(分区日期=今天)
 *   <li>用于历史数据采集的自定义日期分区
 * </ul>
 *
 * <p><b>Example Usage</b>:
 *
 * <pre>{@code
 * // Generate key with today's date
 * String key = ObjectKeyTemplate.generateDailyKey(
 *     "ingest",
 *     "literature-batch",
 *     "pubmed-123-batch-001",
 *     "json"
 * );
 * // Result: ingest/literature-batch/2025/10/26/pubmed-123-batch-001.json
 *
 * // Generate key with specific date
 * String historicalKey = ObjectKeyTemplate.generateDailyKey(
 *     "ingest",
 *     "literature-batch",
 *     "pubmed-456-batch-002",
 *     LocalDate.of(2025, 10, 20),
 *     "json.gz"
 * );
 * // Result: ingest/literature-batch/2025/10/20/pubmed-456-batch-002.json.gz
 *
 * // Use builder for complex scenarios
 * String customKey = ObjectKeyTemplate.builder()
 *     .serviceName("catalog")
 *     .businessType("index_snapshot")
 *     .businessId("snapshot-20251026-001")
 *     .partitionDate(LocalDate.now())
 *     .extension("json.gz")
 *     .customSegment("env", "prod")
 *     .build();
 * }</pre>
 *
 * <p><b>Default Strategy</b>: Uses {@link DatePartitionedKeyGenerator} for all convenience methods.
 *
 * @author linqibin
 * @see ObjectKeyGenerator
 * @see DatePartitionedKeyGenerator
 * @see ObjectKeyContext
 * @since 0.1.0
 */
public final class ObjectKeyTemplate {

  private static final ObjectKeyGenerator DEFAULT_GENERATOR = DatePartitionedKeyGenerator.INSTANCE;

  /** Private constructor to prevent instantiation of utility class. */
  private ObjectKeyTemplate() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Generates a daily-partitioned object key using the current date.
   *
   * <p>This is the most common use case: generating keys for newly created objects with today's
   * date as the partition.
   *
   * <p>Equivalent to: {@code generateDailyKey(service, businessType, businessId, LocalDate.now(),
   * extension)}
   *
   * @param serviceName microservice name (e.g., "ingest", "storage")
   * @param businessType business category (e.g., "literature-batch")
   * @param businessId unique business identifier (e.g., "pubmed-123-batch-001")
   * @param extension file extension (e.g., "json", "json.gz")
   * @return generated object key using today's date
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public static String generateDailyKey(
      String serviceName, String businessType, String businessId, String extension) {
    return generateDailyKey(serviceName, businessType, businessId, LocalDate.now(), extension);
  }

  /**
   * Generates a daily-partitioned object key using a specific partition date.
   *
   * <p>Use this method when:
   *
   * <ul>
   *   <li>Ingesting historical data with original timestamps
   *   <li>Backfilling data for past dates
   *   <li>Re-processing data with original partition dates
   * </ul>
   *
   * <p>Pattern: {@code {service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}}
   *
   * @param serviceName microservice name (e.g., "ingest", "storage")
   * @param businessType business category (e.g., "literature-batch")
   * @param businessId unique business identifier (e.g., "pubmed-123-batch-001")
   * @param partitionDate date for time-based partitioning
   * @param extension file extension (e.g., "json", "json.gz")
   * @return generated object key with specified date partition
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public static String generateDailyKey(
      String serviceName,
      String businessType,
      String businessId,
      LocalDate partitionDate,
      String extension) {
    ObjectKeyContext context =
        ObjectKeyContext.of(serviceName, businessType, businessId, partitionDate, extension);
    return DEFAULT_GENERATOR.generate(context);
  }

  /**
   * Generates an object key using a custom generator strategy.
   *
   * <p>Use this method when you need a non-default key generation pattern (e.g., month-partitioned,
   * hierarchical, or custom business logic).
   *
   * @param context immutable context with all key parameters
   * @param generator custom generator implementation
   * @return generated object key using the specified strategy
   */
  public static String generate(ObjectKeyContext context, ObjectKeyGenerator generator) {
    return generator.generate(context);
  }

  /**
   * Creates a builder for constructing complex object keys with custom segments.
   *
   * <p>Use this when you need to add custom path segments beyond the standard pattern.
   *
   * @return new context builder instance
   */
  public static ObjectKeyContext.Builder builder() {
    return ObjectKeyContext.builder();
  }

  /**
   * Generates an object key using the default daily partitioning generator.
   *
   * <p>This method accepts a fully configured {@link ObjectKeyContext} for maximum flexibility.
   *
   * @param context immutable context with all key parameters
   * @return generated object key using default (daily) partitioning
   */
  public static String generate(ObjectKeyContext context) {
    return DEFAULT_GENERATOR.generate(context);
  }
}
