package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;

/** Strategy contract for individual object storage vendors. */
public interface ObjectStorageProvider {

  /**
   * Provider identifier used for routing and observability tags.
   *
   * @return provider type
   */
  ProviderType getProviderType();

  /** Execute an upload via concrete provider APIs. */
  UploadResult upload(String bucket, String key, InputStream inputStream, ObjectMetadata metadata);
}
