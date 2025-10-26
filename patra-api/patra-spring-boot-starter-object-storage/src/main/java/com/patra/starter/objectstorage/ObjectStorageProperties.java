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
