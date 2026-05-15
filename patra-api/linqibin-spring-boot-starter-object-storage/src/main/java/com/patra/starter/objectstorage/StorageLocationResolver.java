package com.patra.starter.objectstorage;

import dev.linqibin.commons.storage.DatePartitionedKeyGenerator;
import dev.linqibin.commons.storage.ObjectKeyContext;
import dev.linqibin.commons.storage.ObjectKeyGenerator;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/// 存储位置解析器,使用可配置的键生成策略解析存储桶和对象键。
///
/// **路径生成规则:** `{environment`-{serviceName}/{generatedKey}}
///
/// 例如: `dev-patra-ingest/2024/01/15/abc123.pdf`
///
/// **默认键生成器:** {@link DatePartitionedKeyGenerator},按日期分区生成键。
@Slf4j
public class StorageLocationResolver {

  private final String environment;
  private final String serviceName;
  private final ObjectKeyGenerator keyGenerator;

  /// 创建解析器,使用指定的环境、服务名和键生成器。
  ///
  /// @param environment 环境名称(例如 "dev"、"prod")
  /// @param serviceName 服务名称
  /// @param keyGenerator 对象键生成策略(如果为 null 则默认使用 DatePartitionedKeyGenerator)
  public StorageLocationResolver(
      String environment, String serviceName, ObjectKeyGenerator keyGenerator) {
    this.environment = normalizeScope(environment, "dev");
    this.serviceName = normalizeScope(serviceName, "service");
    this.keyGenerator = keyGenerator != null ? keyGenerator : DatePartitionedKeyGenerator.INSTANCE;
  }

  /// 解析存储位置,根据上下文生成存储桶和对象键。
  ///
  /// @param context 存储上下文,包含业务类型、文件名、业务ID等信息
  /// @return 解析后的存储位置
  public StorageLocation resolve(StorageContext context) {
    Objects.requireNonNull(context, "context 不能为 null");
    context.validate();
    String bucket = resolveBucket();
    String objectKey = generateObjectKey(context);
    if (log.isDebugEnabled()) {
      log.debug(
          "已解析存储位置 bucket={} key={} businessId={} businessType={}",
          bucket,
          objectKey,
          context.getBusinessId(),
          context.getBusinessType());
    }
    return new StorageLocation(
        bucket, objectKey, context.getBusinessId(), context.getCorrelationData());
  }

  private String resolveBucket() {
    return environment + '-' + serviceName;
  }

  private String generateObjectKey(StorageContext context) {
    // Extract extension from filename
    String filename = context.getFilename().trim();
    String extension = "";
    int lastDot = filename.lastIndexOf('.');
    if (lastDot > 0 && lastDot < filename.length() - 1) {
      extension = filename.substring(lastDot); // includes the dot
    }

    // Build context for key generator
    ObjectKeyContext keyContext =
        ObjectKeyContext.builder()
            .serviceName(serviceName)
            .businessType(context.getBusinessType())
            .businessId(context.getBusinessId())
            .partitionDate(context.getDate())
            .extension(extension)
            .build();

    return keyGenerator.generate(keyContext);
  }

  private String normalizeScope(String value, String fallback) {
    if (!hasText(value)) {
      return fallback;
    }
    return value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
