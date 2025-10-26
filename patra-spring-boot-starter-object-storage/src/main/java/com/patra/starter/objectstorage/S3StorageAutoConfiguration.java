package com.patra.starter.objectstorage;

import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Auto-configuration for AWS S3 object storage provider.
 *
 * <p>This configuration is only activated when:
 *
 * <ul>
 *   <li>AWS SDK S3 client is on the classpath ({@code @ConditionalOnClass})
 *   <li>Active provider is set to "s3" in configuration ({@code @ConditionalOnProperty})
 * </ul>
 *
 * <p>By isolating S3-specific beans in a separate configuration class with
 * {@code @ConditionalOnClass}, we avoid {@link NoClassDefFoundError} when AWS SDK dependencies are
 * not present. This allows MinIO to be used without requiring AWS SDK on the classpath.
 */
@AutoConfiguration(after = ObjectStorageAutoConfiguration.class)
@ConditionalOnClass(S3Client.class)
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class S3StorageAutoConfiguration {

  @Bean(destroyMethod = "close")
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
  public ObjectStorageProvider s3ObjectStorageProvider(
      S3Client s3Client, ObjectStorageProperties properties) {
    return new S3StorageProvider(s3Client, properties.getMaxFileSize());
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
