package com.patra.starter.objectstorage;

import java.net.URI;
import java.util.Map;
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

/// AWS S3 对象存储提供者自动配置。
/// 
/// **激活条件:**
/// 
/// - AWS SDK S3 客户端在 classpath 中(`@ConditionalOnClass`)
///   - 配置中 active-provider 设置为 "s3"(`@ConditionalOnProperty`)
/// 
/// **设计说明:** 
/// 
/// 通过将 S3 特定的 Bean 隔离在带有 `@ConditionalOnClass` 的独立配置类中, 我们避免了在 AWS SDK 依赖不存在时抛出 {@link
/// NoClassDefFoundError}。 这允许在不引入 AWS SDK 的情况下使用 MinIO。
/// 
/// **配置示例:**
/// 
/// ```java
/// patra:
///   object-storage:
///     active-provider: s3
///     max-file-size: 10485760  # 10MB
///     providers:
///       s3:
///         region: us-east-1
///         access-key: AKIAIOSFODNN7EXAMPLE
///         secret-key: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
///         endpoint: https://s3.amazonaws.com  # 可选,用于自定义端点
/// ```
@AutoConfiguration(after = ObjectStorageAutoConfiguration.class)
@ConditionalOnClass(S3Client.class)
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class S3StorageAutoConfiguration {

  /// 创建 AWS S3 客户端 Bean。
/// 
/// **条件装配:**
/// 
/// - 当 active-provider=s3 时激活
///   - 当容器中不存在 {@link S3Client} Bean 时创建
/// 
/// **支持自定义端点:** 如果配置了 endpoint,将覆盖默认的 AWS S3 端点。 这对于使用 S3 兼容服务(如 MinIO)非常有用。
/// 
/// @param properties 对象存储配置属性
/// @return 配置好的 S3 客户端
/// @throws IllegalStateException 如果缺少必需的配置项(region、access-key、secret-key)
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

  /// 创建 S3 存储提供者 Bean。
/// 
/// **条件装配:** 当容器中存在 {@link S3Client} Bean 时激活。
/// 
/// @param s3Client S3 客户端
/// @param properties 对象存储配置属性
/// @return S3 存储提供者
  @Bean
  @ConditionalOnMissingBean(ObjectStorageProvider.class)
  @ConditionalOnBean(S3Client.class)
  public ObjectStorageProvider s3ObjectStorageProvider(
      S3Client s3Client, ObjectStorageProperties properties) {
    return new S3StorageProvider(s3Client, properties.getMaxFileSize());
  }

  /// 解析指定提供者的配置。
/// 
/// @param properties 对象存储配置属性
/// @param providerKey 提供者键(minio 或 s3)
/// @return 提供者配置
/// @throws IllegalStateException 如果找不到指定提供者的配置
  private ObjectStorageProperties.ProviderConfig resolveConfig(
      ObjectStorageProperties properties, String providerKey) {
    return properties.getProviders().entrySet().stream()
        .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(providerKey))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("缺少对象存储提供者配置: " + providerKey));
  }

  /// 验证配置值是否存在。
/// 
/// @param value 配置值
/// @param field 字段名称
/// @return 配置值
/// @throws IllegalStateException 如果配置值为空
  private static String require(String value, String field) {
    if (!hasText(value)) {
      throw new IllegalStateException("对象存储配置项 " + field + " 不能为空");
    }
    return value;
  }

  /// 检查字符串是否有内容。
/// 
/// @param value 待检查的字符串
/// @return 如果字符串非空且包含非空白字符则返回 true
  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
