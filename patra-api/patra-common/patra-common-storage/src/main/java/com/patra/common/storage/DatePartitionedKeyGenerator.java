package com.patra.common.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 按日期分区的对象键生成器,遵循模式: {@code {service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}}
 *
 * <p>此实现创建具有每日时间分区的对象键,支持基于日期范围的高效生命周期管理、成本分析和查询优化。
 *
 * <p><b>模式结构</b>:
 *
 * <ul>
 *   <li><b>Service</b>: 微服务名称(规范化为小写)
 *   <li><b>Business Type</b>: 业务类别(规范化为 kebab-case)
 *   <li><b>Date Partition</b>: 三级层次结构(yyyy/MM/dd)
 *   <li><b>Business ID</b>: 唯一标识符(保持原样)
 *   <li><b>Extension</b>: 文件扩展名,包括复合类型(例如 json.gz)
 * </ul>
 *
 * <p><b>输出示例</b>:
 *
 * <pre>{@code
 * ingest/publication-batch/2025/10/26/pubmed-123-batch-001.json
 * storage/metadata-snapshot/2025/10/25/snapshot-20251025-001.json.gz
 * literature/index/2025/10/26/index-pmid-12345.xml
 * }</pre>
 *
 * <p><b>规范化规则</b>:
 *
 * <ul>
 *   <li>服务名称 → 小写
 *   <li>业务类型 → 小写,下划线转换为连字符(kebab-case)
 *   <li>扩展名 → 移除前导点(如果存在)
 * </ul>
 *
 * <p><b>线程安全</b>: 此类是无状态且线程安全的。单例实例 {@link #INSTANCE} 可以在整个应用程序中安全共享。
 *
 * @author linqibin
 * @see ObjectKeyGenerator
 * @see ObjectKeyContext
 * @since 0.1.0
 */
public final class DatePartitionedKeyGenerator implements ObjectKeyGenerator {

  /** 用于整个应用程序共享使用的单例实例。 */
  public static final DatePartitionedKeyGenerator INSTANCE = new DatePartitionedKeyGenerator();

  private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
  private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd");

  /** 私有构造函数以强制单例模式。 */
  private DatePartitionedKeyGenerator() {}

  /**
   * 从提供的上下文生成按日期分区的对象键。
   *
   * <p>生成的键遵循模式: {@code {service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}}
   *
   * @param context 包含所有键生成参数的不可变上下文
   * @return 生成的对象键路径
   * @throws IllegalArgumentException 如果上下文包含无效数据
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
   *   <li>"patra-registry" → "patra-registry"
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
   *   <li>"publication_batch" → "publication-batch"
   *   <li>"PublicationBatch" → "literaturebatch"
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
