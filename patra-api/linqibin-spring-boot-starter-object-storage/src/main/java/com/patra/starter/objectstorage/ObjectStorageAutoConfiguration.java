package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.metrics.ObjectStorageMetrics;
import dev.linqibin.commons.storage.ObjectKeyGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.MinioClient;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.retry.support.RetryTemplate;

/// 对象存储自动配置入口点,暴露 {@link ObjectStorageTemplate} Bean。
///
/// 此配置类负责:
///
/// - 创建对象存储指标收集器 {@link ObjectStorageMetrics}
///   - 配置重试模板 {@link RetryTemplate},处理瞬时故障
///   - 根据 active-provider 配置初始化 MinIO 客户端(默认提供者)
///   - 创建 MinIO 存储提供者 {@link MinioStorageProvider}
///   - 组装对象存储模板 {@link ObjectStorageTemplate}
///   - 创建存储位置解析器 {@link StorageLocationResolver}
///
/// **配置示例:**
///
/// ```java
/// patra:
///   object-storage:
///     active-provider: minio  # 默认使用 MinIO
///     max-file-size: 10485760  # 10MB
///     retry:
///       max-attempts: 3
///       wait-duration: 1000
///     providers:
///       minio:
///         endpoint: http://localhost:9000
///         access-key: minioadmin
///         secret-key: minioadmin
/// ```
@AutoConfiguration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageAutoConfiguration {

  /// 创建对象存储指标收集器,用于监控上传/下载操作的性能和成功率。
  ///
  /// @param meterRegistry Micrometer 度量注册表
  /// @return 对象存储指标收集器
  @Bean
  @ConditionalOnMissingBean
  public ObjectStorageMetrics objectStorageMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
    return new ObjectStorageMetrics(meterRegistry.getIfAvailable());
  }

  /// 创建重试模板,配置为仅重试瞬时故障(transient failures)。
  ///
  /// **可重试异常(瞬时故障):**
  ///
  /// - {@link java.io.IOException} - 网络错误、连接失败
  ///   - {@link java.net.SocketTimeoutException} - 读写超时
  ///   - {@link java.net.ConnectException} - 连接被拒绝
  ///
  /// **不可重试异常(永久故障):**
  ///
  /// - {@link com.patra.starter.objectstorage.domain.InvalidUploadRequestException} - 无效参数
  ///   - 认证失败
  ///   - 授权失败
  ///
  /// **重试策略:** 使用指数退避算法,基础延迟 100ms,最大延迟 30 秒。
  ///
  /// @param properties 包含重试设置的配置属性
  /// @return 配置了指数退避的重试模板
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
        .retryOn(IOException.class)
        .retryOn(SocketTimeoutException.class)
        .retryOn(ConnectException.class)
        .traversingCauses()
        .build();
  }

  /// 创建 MinIO 客户端 Bean。
  ///
  /// **条件装配:**
  ///
  /// - 当 active-provider=minio 时激活(默认值)
  ///   - 当容器中不存在 {@link MinioClient} Bean 时创建
  ///
  /// @param properties 对象存储配置属性
  /// @return 配置好的 MinIO 客户端
  /// @throws IllegalStateException 如果缺少必需的配置项(endpoint、access-key、secret-key)
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

  /// 创建 MinIO 存储提供者 Bean。
  ///
  /// **条件装配:** 当容器中存在 {@link MinioClient} Bean 时激活。
  ///
  /// @param minioClient MinIO 客户端
  /// @param properties 对象存储配置属性
  /// @return MinIO 存储提供者
  @Bean
  @ConditionalOnMissingBean(ObjectStorageProvider.class)
  @ConditionalOnBean(MinioClient.class)
  public ObjectStorageProvider minioObjectStorageProvider(
      MinioClient minioClient, ObjectStorageProperties properties) {
    return new MinioStorageProvider(minioClient, properties.getMaxFileSize());
  }

  /// 创建对象存储模板 Bean,作为对象存储操作的统一入口。
  ///
  /// **条件装配:** 当容器中存在 {@link ObjectStorageProvider} Bean 时激活。
  ///
  /// @param provider 对象存储提供者(MinIO 或 S3)
  /// @param retryTemplate 重试模板
  /// @param metrics 指标收集器
  /// @return 对象存储模板
  @Bean
  @ConditionalOnMissingBean(ObjectStorageOperations.class)
  @ConditionalOnBean(ObjectStorageProvider.class)
  public ObjectStorageTemplate objectStorageTemplate(
      ObjectStorageProvider provider, RetryTemplate retryTemplate, ObjectStorageMetrics metrics) {
    return new ObjectStorageTemplate(provider, retryTemplate, metrics);
  }

  /// 创建存储位置解析器,负责生成对象键的完整路径。
  ///
  /// **路径生成规则:** `{profile`/{service}/{generated-key}}
  ///
  /// 例如: `prod/patra-ingest/2024/01/15/abc123.pdf`
  ///
  /// @param environment Spring 环境对象,用于读取 profile 和服务名
  /// @param keyGeneratorProvider 对象键生成器提供者(可选)
  /// @return 存储位置解析器
  @Bean
  @ConditionalOnMissingBean
  public StorageLocationResolver storageLocationResolver(
      Environment environment, ObjectProvider<ObjectKeyGenerator> keyGeneratorProvider) {
    String profile = environment.getProperty("spring.profiles.active", "dev");
    String service = environment.getProperty("spring.application.name", "service");
    ObjectKeyGenerator keyGenerator = keyGeneratorProvider.getIfAvailable();
    return new StorageLocationResolver(profile, service, keyGenerator);
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
