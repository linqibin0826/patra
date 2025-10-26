package com.patra.storage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/** Request body used by callers to persist upload metadata. */
public record UploadRecordRequest(
    @NotBlank String bucketName,
    @NotBlank String objectKey,
    @PositiveOrZero long fileSize,
    @Size(max = 128) String contentType,
    @NotBlank String md5Hash,
    String sha256Hash,
    @NotBlank String serviceName,
    @NotBlank String businessType,
    @NotBlank String businessId,
    Map<String, Object> correlationData,
    @NotBlank String providerType,
    Instant expiresAt,
    @Size(max = 512) String recordRemarks) {

  /** Canonical constructor normalizing optional fields. */
  public UploadRecordRequest {
    correlationData = correlationData == null ? Map.of() : Map.copyOf(correlationData);
  }

  /**
   * @return canonical storage key in <code>bucket/objectKey</code> form.
   */
  public String storageKey() {
    return bucketName + "/" + objectKey;
  }
}
