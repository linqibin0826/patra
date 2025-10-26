package com.patra.common.objectstorage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Resolves storage bucket and object key using fixed formatting rules. */
@Slf4j
public class StorageLocationResolver {

  private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
  private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd");

  private final String environment;
  private final String serviceName;

  public StorageLocationResolver(String environment, String serviceName) {
    this.environment = normalizeScope(environment, "dev");
    this.serviceName = normalizeScope(serviceName, "service");
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
    LocalDate date = context.getDate();
    String year = YEAR_FORMATTER.format(date);
    String month = MONTH_FORMATTER.format(date);
    String day = DAY_FORMATTER.format(date);
    String businessType = normalizeBusinessType(context.getBusinessType());
    String filename = context.getFilename().trim();
    return businessType + '/' + year + '/' + month + '/' + day + '/' + filename;
  }

  private String normalizeBusinessType(String businessType) {
    String normalized = businessType.trim().toLowerCase(Locale.ROOT);
    normalized = normalized.replace('_', '-');
    normalized = normalized.replace(' ', '-');
    return normalized;
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
