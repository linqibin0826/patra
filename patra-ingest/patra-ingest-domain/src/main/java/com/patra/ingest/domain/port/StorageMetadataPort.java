package com.patra.ingest.domain.port;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;

/**
 * Output port for recording upload metadata to patra-storage service.
 *
 * <p>This port integrates with the patra-storage bounded context (separate microservice) to
 * register file uploads in the system-wide storage catalog. The catalog tracks all uploaded files
 * with business context, checksums, and correlation data for auditing and retrieval.
 *
 * <p>Infrastructure adapters implement this via Feign RPC clients or similar service integration
 * mechanisms.
 */
public interface StorageMetadataPort {

  /**
   * Records upload metadata for a stored file.
   *
   * @param request metadata request containing file info and business context
   * @return metadata result with catalog record identifier
   */
  MetadataResult recordUpload(MetadataRequest request);

  /**
   * Metadata request for recording file upload information.
   *
   * @param storageKey complete storage identifier (bucket/key combined)
   * @param bucketName object storage bucket name
   * @param objectKey object key within bucket
   * @param fileSize file size in bytes
   * @param contentType MIME content type
   * @param md5 MD5 checksum (hex format)
   * @param sha256 SHA-256 checksum (hex format)
   * @param serviceName originating service name
   * @param businessType business type classification
   * @param businessId business identifier for correlation
   * @param correlation additional correlation metadata
   * @param providerType storage provider type (MINIO, S3, etc.)
   * @param remarks optional remarks for auditing
   */
  @Builder
  record MetadataRequest(
      String storageKey,
      String bucketName,
      String objectKey,
      long fileSize,
      String contentType,
      String md5,
      String sha256,
      String serviceName,
      String businessType,
      String businessId,
      Map<String, Object> correlation,
      String providerType,
      String remarks) {}

  /**
   * Metadata result containing catalog record information.
   *
   * @param metadataId catalog record identifier from patra-storage
   * @param recordedAt timestamp when metadata was recorded
   */
  @Builder
  record MetadataResult(Long metadataId, Instant recordedAt) {}
}
