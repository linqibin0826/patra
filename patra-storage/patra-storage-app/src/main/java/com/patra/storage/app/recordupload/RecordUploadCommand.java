package com.patra.storage.app.recordupload;

import java.time.Instant;
import java.util.Map;

/** Command DTO describing a successful upload that must be recorded. */
public record RecordUploadCommand(
    String bucketName,
    String objectKey,
    long fileSize,
    String contentType,
    String md5Hash,
    String sha256Hash,
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData,
    String providerType,
    Instant expiresAt,
    byte[] ipAddress,
    String recordRemarks) {

  /** Canonical constructor trimming mutable inputs. */
  public RecordUploadCommand {
    correlationData = correlationData == null ? Map.of() : Map.copyOf(correlationData);
    ipAddress = ipAddress == null ? null : ipAddress.clone();
  }
}
