package com.patra.starter.objectstorage;

import com.patra.common.storage.DatePartitionedKeyGenerator;
import com.patra.common.storage.ObjectKeyContext;
import com.patra.common.storage.ObjectKeyGenerator;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Resolves storage bucket and object key using configurable key generation strategy. */
@Slf4j
public class StorageLocationResolver {

  private final String environment;
  private final String serviceName;
  private final ObjectKeyGenerator keyGenerator;

  /**
   * Creates a resolver with the specified environment, service name, and key generator.
   *
   * @param environment environment name (e.g., "dev", "prod")
   * @param serviceName service name
   * @param keyGenerator strategy for generating object keys (defaults to
   *     DatePartitionedKeyGenerator if null)
   */
  public StorageLocationResolver(
      String environment, String serviceName, ObjectKeyGenerator keyGenerator) {
    this.environment = normalizeScope(environment, "dev");
    this.serviceName = normalizeScope(serviceName, "service");
    this.keyGenerator = keyGenerator != null ? keyGenerator : DatePartitionedKeyGenerator.INSTANCE;
  }

  public StorageLocation resolve(StorageContext context) {
    Objects.requireNonNull(context, "context must not be null");
    context.validate();
    String bucket = resolveBucket();
    String objectKey = generateObjectKey(context);
    if (log.isDebugEnabled()) {
      log.debug(
          "Resolved storage location bucket={} key={} businessId={} businessType={}",
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
