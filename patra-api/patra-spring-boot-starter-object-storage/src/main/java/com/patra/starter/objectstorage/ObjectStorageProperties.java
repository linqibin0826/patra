package com.patra.starter.objectstorage;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 对象存储 starter 的配置模型。 */
@ConfigurationProperties("patra.object-storage")
@Data
public class ObjectStorageProperties {

  /** 活跃的提供商标识符 (minio 或 s3)。 */
  private String activeProvider = "minio";

  /** 按提供商 ID 键控的各个提供商配置。 */
  private Map<String, ProviderConfig> providers = new HashMap<>();

  /** 应用于模板操作的重试设置。 */
  private RetryConfig retry = new RetryConfig();

  /**
   * 上传的最大文件大小(字节),默认: 100MB。
   *
   * <p>此限制可防止上传过大文件导致的内存不足错误。 超过此大小的上传将失败并抛出 {@link
   * com.patra.starter.objectstorage.domain.InvalidUploadRequestException}。
   */
  private long maxFileSize = 104857600L; // 100MB

  /**
   * 获取指定提供商的配置。
   *
   * @param providerId 提供商 ID
   * @return 提供商配置,如果不存在则返回 null
   */
  public ProviderConfig getProviderConfig(String providerId) {
    if (providerId == null) {
      return null;
    }
    return providers.get(providerId);
  }

  /** 提供商配置类。 */
  @Data
  public static class ProviderConfig {
    /** 端点 URL */
    private String endpoint;

    /** 区域 */
    private String region;

    /** 访问密钥 */
    private String accessKey;

    /** 秘密密钥 */
    private String secretKey;

    /** 存储桶名称 */
    private String bucket;
  }

  /** 重试配置类。 */
  @Data
  public static class RetryConfig {
    /** 最大尝试次数 */
    private int maxAttempts = 3;

    /** 等待时长(毫秒) */
    private long waitDuration = 1000L;
  }
}
