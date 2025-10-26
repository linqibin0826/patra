package com.patra.starter.objectstorage;

import com.patra.common.objectstorage.StorageLocationResolver;
import com.patra.starter.objectstorage.metrics.ObjectStorageMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.MinioClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.retry.support.RetryTemplate;

/** Auto-configuration entry point that exposes {@link ObjectStorageTemplate}. */
@AutoConfiguration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ObjectStorageMetrics objectStorageMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
    return new ObjectStorageMetrics(meterRegistry.getIfAvailable());
  }

  /**
   * Creates a retry template configured to retry only transient failures.
   *
   * <p>Retryable exceptions (transient failures):
   *
   * <ul>
   *   <li>{@link java.io.IOException} - Network errors, connection failures
   *   <li>{@link java.net.SocketTimeoutException} - Read/write timeouts
   *   <li>{@link java.net.ConnectException} - Connection refused
   * </ul>
   *
   * <p>Non-retryable exceptions (permanent failures):
   *
   * <ul>
   *   <li>{@link com.patra.starter.objectstorage.domain.InvalidUploadRequestException} - Invalid
   *       arguments
   *   <li>Authentication failures
   *   <li>Authorization failures
   * </ul>
   *
   * @param properties configuration properties containing retry settings
   * @return configured retry template with exponential backoff
   */
  @Bean
  @ConditionalOnMissingBean
  public RetryTemplate objectStorageRetryTemplate(ObjectStorageProperties properties) {
    var retry = properties.getRetry();
    int maxAttempts = Math.max(1, retry.getMaxAttempts());
    long baseDelay = Math.max(100L, retry.getWaitDuration());
    long maxDelay = Math.max(baseDelay, Math.min(baseDelay * 8, 30_000L));
    return RetryTemplate.builder()
        .maxAttempts(maxAttempts)
        .exponentialBackoff(baseDelay, 2.0, maxDelay)
        .retryOn(java.io.IOException.class)
        .retryOn(java.net.SocketTimeoutException.class)
        .retryOn(java.net.ConnectException.class)
        .traversingCauses()
        .build();
  }

  @Bean
  @ConditionalOnMissingBean(MinioClient.class)
  @ConditionalOnProperty(
      prefix = "patra.object-storage",
      name = "active-provider",
      havingValue = "minio",
      matchIfMissing = true)
  public MinioClient minioClient(ObjectStorageProperties properties) {
    var config = resolveConfig(properties, "minio");
    return MinioClient.builder()
        .endpoint(require(config.getEndpoint(), "endpoint"))
        .credentials(
            require(config.getAccessKey(), "access-key"),
            require(config.getSecretKey(), "secret-key"))
        .build();
  }

  @Bean
  @ConditionalOnMissingBean(ObjectStorageProvider.class)
  @ConditionalOnBean(MinioClient.class)
  public ObjectStorageProvider minioObjectStorageProvider(
      MinioClient minioClient, ObjectStorageProperties properties) {
    return new MinioStorageProvider(minioClient, properties.getMaxFileSize());
  }

  @Bean
  @ConditionalOnMissingBean(ObjectStorageOperations.class)
  @ConditionalOnBean(ObjectStorageProvider.class)
  public ObjectStorageTemplate objectStorageTemplate(
      ObjectStorageProvider provider, RetryTemplate retryTemplate, ObjectStorageMetrics metrics) {
    return new ObjectStorageTemplate(provider, retryTemplate, metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public StorageLocationResolver storageLocationResolver(Environment environment) {
    String profile = environment.getProperty("spring.profiles.active", "dev");
    String service = environment.getProperty("spring.application.name", "service");
    return new StorageLocationResolver(profile, service);
  }

  private ObjectStorageProperties.ProviderConfig resolveConfig(
      ObjectStorageProperties properties, String providerKey) {
    return properties.getProviders().entrySet().stream()
        .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(providerKey))
        .map(java.util.Map.Entry::getValue)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Missing configuration for object storage provider: " + providerKey));
  }

  private static String require(String value, String field) {
    if (!hasText(value)) {
      throw new IllegalStateException("Object storage " + field + " must be configured");
    }
    return value;
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
