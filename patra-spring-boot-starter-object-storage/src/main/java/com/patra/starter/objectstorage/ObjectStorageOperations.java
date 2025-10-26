package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;

/**
 * Unified operations for interacting with an object storage provider.
 *
 * <p>This interface abstracts over different object storage vendors (MinIO, AWS S3, etc.) to
 * provide a consistent API for file storage operations.
 *
 * <p><b>Current Implementation Scope (Phase 1):</b>
 *
 * <ul>
 *   <li>✅ {@link #upload} - Upload objects to storage
 *   <li>⏳ Download operations - Planned for Phase 2
 *   <li>⏳ Delete operations - Planned for Phase 2
 *   <li>⏳ Existence checks - Planned for Phase 2
 *   <li>⏳ Metadata retrieval - Planned for Phase 2
 * </ul>
 *
 * <p>This incremental approach allows us to deliver core functionality quickly while gathering
 * requirements for additional operations.
 *
 * @see ObjectStorageTemplate for the default implementation with retry and metrics
 * @see ObjectStorageProvider for the provider abstraction
 */
public interface ObjectStorageOperations {

  /**
   * Upload the content and return metadata about the stored object.
   *
   * @param bucket bucket name that already exists or will be created lazily
   * @param key unique object key within the bucket
   * @param inputStream content to upload
   * @param metadata auxiliary metadata such as size and content type
   * @return upload result containing storage key and etag
   */
  UploadResult upload(String bucket, String key, InputStream inputStream, ObjectMetadata metadata);
}
