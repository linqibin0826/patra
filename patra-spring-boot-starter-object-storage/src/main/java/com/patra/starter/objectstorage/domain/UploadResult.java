package com.patra.starter.objectstorage.domain;

import lombok.Builder;
import lombok.Getter;

/** Outcome of an upload operation. */
@Getter
@Builder
public class UploadResult {
  private final String storageKey;
  private final String bucketName;
  private final String objectKey;
  private final String etag;
  private final long fileSize;
}
