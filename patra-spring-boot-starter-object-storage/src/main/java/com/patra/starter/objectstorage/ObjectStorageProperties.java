package com.patra.starter.objectstorage;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration model for object storage starter. */
@ConfigurationProperties("patra.object-storage")
@Data
public class ObjectStorageProperties {

  /** Active provider identifier (minio or s3). */
  private String activeProvider = "minio";

  /** Individual provider configurations keyed by provider id. */
  private Map<String, ProviderConfig> providers = new HashMap<>();

  /** Retry settings applied to template operations. */
  private RetryConfig retry = new RetryConfig();

  /**
   * Maximum file size in bytes for uploads (default: 100MB).
   *
   * <p>This limit protects against out-of-memory errors from uploading excessively large files.
   * Uploads exceeding this size will fail with {@link
   * com.patra.starter.objectstorage.domain.InvalidUploadRequestException}.
   */
  private long maxFileSize = 104857600L; // 100MB

  public ProviderConfig getProviderConfig(String providerId) {
    if (providerId == null) {
      return null;
    }
    return providers.get(providerId);
  }

  @Data
  public static class ProviderConfig {
    private String endpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private String bucket;
  }

  @Data
  public static class RetryConfig {
    private int maxAttempts = 3;
    private long waitDuration = 1000L;
  }
}
