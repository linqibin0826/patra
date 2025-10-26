package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.metrics.ObjectStorageMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.MinioClient;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** Auto-configuration entry point that exposes {@link ObjectStorageTemplate}. */
@AutoConfiguration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ObjectStorageMetrics objectStorageMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
    return new ObjectStorageMetrics(meterRegistry.getIfAvailable());
  }

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
  public ObjectStorageProvider minioObjectStorageProvider(MinioClient minioClient) {
    return new MinioStorageProvider(minioClient);
  }

  @Bean
  @ConditionalOnMissingBean(S3Client.class)
  @ConditionalOnProperty(
      prefix = "patra.object-storage",
      name = "active-provider",
      havingValue = "s3")
  public S3Client s3Client(ObjectStorageProperties properties) {
    var config = resolveConfig(properties, "s3");
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(require(config.getRegion(), "region")))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        require(config.getAccessKey(), "access-key"),
                        require(config.getSecretKey(), "secret-key"))));
    if (hasText(config.getEndpoint())) {
      builder.endpointOverride(URI.create(config.getEndpoint()));
    }
    return builder.build();
  }

  @Bean
  @ConditionalOnMissingBean(ObjectStorageProvider.class)
  @ConditionalOnBean(S3Client.class)
  public ObjectStorageProvider s3ObjectStorageProvider(S3Client s3Client) {
    return new S3StorageProvider(s3Client);
  }

  @Bean
  @ConditionalOnMissingBean(ObjectStorageOperations.class)
  @ConditionalOnBean(ObjectStorageProvider.class)
  public ObjectStorageTemplate objectStorageTemplate(
      ObjectStorageProvider provider, RetryTemplate retryTemplate, ObjectStorageMetrics metrics) {
    return new ObjectStorageTemplate(provider, retryTemplate, metrics);
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
