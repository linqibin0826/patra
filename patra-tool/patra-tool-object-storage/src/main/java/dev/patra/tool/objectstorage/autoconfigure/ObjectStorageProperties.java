package dev.patra.tool.objectstorage.autoconfigure;

import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// 对象存储配置，前缀 `patra.object-storage`。
///
/// @param enabled 总开关
/// @param activeProvider 生效的 provider id（`minio` / `s3`）
/// @param providers 各 provider 的连接参数
/// @param retry 上传下载重试
/// @param maxFileSize 单文件上限（字节）
@Validated
@ConfigurationProperties(prefix = "patra.object-storage")
public record ObjectStorageProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("minio") String activeProvider,
    @DefaultValue Map<String, Provider> providers,
    @DefaultValue Retry retry,
    @DefaultValue("104857600") @Min(1) long maxFileSize) {
  public ObjectStorageProperties {
    providers = Map.copyOf(providers);
  }

  /// 一个 provider 的连接参数。
  ///
  /// @param endpoint 端点（MinIO 必填，S3 可选）
  /// @param region 区域（S3 必填）
  /// @param accessKey 访问密钥
  /// @param secretKey 私密密钥
  public record Provider(String endpoint, String region, String accessKey, String secretKey) {}

  /// 重试（毫秒）。
  ///
  /// @param maxAttempts 最多尝试次数
  /// @param waitDuration 首次退避毫秒数
  public record Retry(
      @DefaultValue("3") @Min(1) int maxAttempts,
      @DefaultValue("1000") @Min(0) long waitDuration) {}
}
