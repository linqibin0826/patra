package dev.linqibin.commons.storage;

import java.time.LocalDate;

/// 提供便捷工厂方法的静态工具类,用于生成标准化对象存储键。
///
/// 此类充当 {@link ObjectKeyGenerator} 策略实现的门面, 为常见用例提供简化的 API,无需直接实例化生成器。
///
/// **主要用例**:
///
/// - 使用默认(每日)分区快速生成键
///   - 当前日期分区(分区日期=今天)
///   - 用于历史数据采集的自定义日期分区
///
/// **示例用法**:
///
/// ```java
/// // 使用今天的日期生成键
/// String key = ObjectKeyTemplate.generateDailyKey(
///     "ingest",
///     "publication-batch",
///     "pubmed-123-batch-001",
///     "json"
/// );
/// // 结果: ingest/publication-batch/2025/10/26/pubmed-123-batch-001.json
///
/// // 使用指定日期生成键
/// String historicalKey = ObjectKeyTemplate.generateDailyKey(
///     "ingest",
///     "publication-batch",
///     "pubmed-456-batch-002",
///     LocalDate.of(2025, 10, 20),
///     "json.gz"
/// );
/// // 结果: ingest/publication-batch/2025/10/20/pubmed-456-batch-002.json.gz
///
/// // 对于复杂场景使用构建器
/// String customKey = ObjectKeyTemplate.builder()
///     .serviceName("publication")
///     .businessType("index_snapshot")
///     .businessId("snapshot-20251026-001")
///     .partitionDate(LocalDate.now())
///     .extension("json.gz")
///     .customSegment("env", "prod")
///     .build();
/// ```
///
/// **默认策略**: 所有便捷方法使用 {@link DatePartitionedKeyGenerator}。
///
/// @author linqibin
/// @see ObjectKeyGenerator
/// @see DatePartitionedKeyGenerator
/// @see ObjectKeyContext
/// @since 0.1.0
public final class ObjectKeyTemplate {

  private static final ObjectKeyGenerator DEFAULT_GENERATOR = DatePartitionedKeyGenerator.INSTANCE;

  /// 私有构造函数,防止实例化工具类。
  private ObjectKeyTemplate() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /// 使用当前日期生成按日分区的对象键。
  ///
  /// 这是最常见的用例:使用今天的日期作为分区为新创建的对象生成键。
  ///
  /// 等效于: {@code generateDailyKey(service, businessType, businessId, LocalDate.now(),
  /// extension)}
  ///
  /// @param serviceName 微服务名称(例如 "ingest"、"storage")
  /// @param businessType 业务类别(例如 "publication-batch")
  /// @param businessId 唯一业务标识符(例如 "pubmed-123-batch-001")
  /// @param extension 文件扩展名(例如 "json"、"json.gz")
  /// @return 使用今天日期生成的对象键
  /// @throws IllegalArgumentException 如果任何参数无效
  public static String generateDailyKey(
      String serviceName, String businessType, String businessId, String extension) {
    return generateDailyKey(serviceName, businessType, businessId, LocalDate.now(), extension);
  }

  /// 使用指定分区日期生成按日分区的对象键。
  ///
  /// 在以下情况下使用此方法:
  ///
  /// - 采集具有原始时间戳的历史数据
  ///   - 回填过去日期的数据
  ///   - 使用原始分区日期重新处理数据
  ///
  /// 模式: `{service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}`
  ///
  /// @param serviceName 微服务名称(例如 "ingest"、"storage")
  /// @param businessType 业务类别(例如 "publication-batch")
  /// @param businessId 唯一业务标识符(例如 "pubmed-123-batch-001")
  /// @param partitionDate 用于基于时间分区的日期
  /// @param extension 文件扩展名(例如 "json"、"json.gz")
  /// @return 使用指定日期分区生成的对象键
  /// @throws IllegalArgumentException 如果任何参数无效
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

  /// 使用自定义生成器策略生成对象键。
  ///
  /// 当您需要非默认键生成模式(例如月分区、分层或自定义业务逻辑)时使用此方法。
  ///
  /// @param context 包含所有键参数的不可变上下文
  /// @param generator 自定义生成器实现
  /// @return 使用指定策略生成的对象键
  public static String generate(ObjectKeyContext context, ObjectKeyGenerator generator) {
    return generator.generate(context);
  }

  /// 创建用于构建包含自定义段的复杂对象键的构建器。
  ///
  /// 当您需要在标准模式之外添加自定义路径段时使用此方法。
  ///
  /// @return 新的上下文构建器实例
  public static ObjectKeyContext.Builder builder() {
    return ObjectKeyContext.builder();
  }

  /// 使用默认的每日分区生成器生成对象键。
  ///
  /// 此方法接受完全配置的 {@link ObjectKeyContext} 以获得最大灵活性。
  ///
  /// @param context 包含所有键参数的不可变上下文
  /// @return 使用默认(每日)分区生成的对象键
  public static String generate(ObjectKeyContext context) {
    return DEFAULT_GENERATOR.generate(context);
  }
}
