package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;

/** Unified operations for interacting with an object storage provider. */
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
