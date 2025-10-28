package com.patra.ingest.domain.port;

import com.patra.common.model.StandardLiterature;
import java.util.List;
import lombok.Builder;

/**
 * Output port for storing literature payloads to object storage.
 *
 * <p>This port abstracts the technical details of uploading serialized literature to S3/MinIO or
 * similar object storage systems. Infrastructure adapters implement file serialization, checksum
 * calculation, and physical upload.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Serialize literature to JSON format
 *   <li>Calculate file checksums (MD5, SHA-256)
 *   <li>Upload to object storage with metadata
 *   <li>Return storage location and file information
 * </ul>
 */
public interface LiteratureStoragePort {

  /**
   * Stores a batch of standardized literature to object storage.
   *
   * @param literature domain-normalized literature list
   * @param context storage context with execution metadata
   * @return storage result containing location and checksums
   */
  StorageResult store(List<StandardLiterature> literature, StorageContext context);

  /**
   * Storage result containing file location and integrity information.
   *
   * @param storageKey complete storage identifier (bucket/key combined)
   * @param bucketName object storage bucket name
   * @param objectKey object key within bucket
   * @param fileSize file size in bytes
   * @param md5 MD5 checksum (hex format)
   * @param sha256 SHA-256 checksum (hex format)
   * @param literatureCount number of literature items stored
   */
  @Builder
  record StorageResult(
      String storageKey,
      String bucketName,
      String objectKey,
      long fileSize,
      String md5,
      String sha256,
      int literatureCount) {}

  /**
   * Storage context providing execution metadata for file organization.
   *
   * @param runId task run identifier
   * @param batchNo execution batch number
   * @param provenanceCode normalized source identifier
   */
  @Builder
  record StorageContext(Long runId, int batchNo, String provenanceCode) {}
}
