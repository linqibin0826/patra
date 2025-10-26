package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadFailedException;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/** AWS S3 implementation of {@link ObjectStorageProvider}. */
@RequiredArgsConstructor
public class S3StorageProvider implements ObjectStorageProvider {

  private final S3Client s3Client;

  @Override
  public ProviderType getProviderType() {
    return ProviderType.S3;
  }

  @Override
  public UploadResult upload(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    try {
      validateArguments(bucket, key, inputStream, metadata);
      Map<String, String> userMetadata =
          metadata.getUserMetadata() == null
              ? Collections.emptyMap()
              : new HashMap<>(metadata.getUserMetadata());

      PutObjectRequest request =
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(metadata.getContentType())
              .metadata(userMetadata)
              .build();

      PutObjectResponse response =
          s3Client.putObject(
              request, RequestBody.fromInputStream(inputStream, metadata.getContentLength()));

      return UploadResult.builder()
          .storageKey(bucket + "/" + key)
          .bucketName(bucket)
          .objectKey(key)
          .etag(response.eTag())
          .fileSize(metadata.getContentLength())
          .build();
    } catch (Exception ex) {
      throw new UploadFailedException("S3 upload failed", ex);
    }
  }

  private void validateArguments(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    if (bucket == null || bucket.isBlank() || key == null || key.isBlank()) {
      throw new UploadFailedException("Bucket and key must be provided");
    }
    if (inputStream == null) {
      throw new UploadFailedException("Input stream cannot be null");
    }
    if (metadata == null || metadata.getContentLength() <= 0) {
      throw new UploadFailedException("Object metadata with content length is required");
    }
  }
}
