package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadFailedException;
import com.patra.starter.objectstorage.domain.UploadResult;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** MinIO implementation of {@link ObjectStorageProvider}. */
@Slf4j
@RequiredArgsConstructor
public class MinioStorageProvider implements ObjectStorageProvider {

  private final MinioClient minioClient;

  @Override
  public ProviderType getProviderType() {
    return ProviderType.MINIO;
  }

  @Override
  public UploadResult upload(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    try {
      validateArguments(bucket, key, inputStream, metadata);
      ensureBucketExists(bucket);
      ObjectWriteResponse response =
          minioClient.putObject(
              PutObjectArgs.builder().bucket(bucket).object(key).stream(
                      inputStream, metadata.getContentLength(), -1)
                  .contentType(metadata.getContentType())
                  .build());
      return UploadResult.builder()
          .storageKey(bucket + "/" + key)
          .bucketName(bucket)
          .objectKey(key)
          .etag(response.etag())
          .fileSize(metadata.getContentLength())
          .build();
    } catch (Exception ex) {
      throw new UploadFailedException("MinIO upload failed", ex);
    }
  }

  private void ensureBucketExists(String bucket) throws Exception {
    boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      log.info("Created MinIO bucket: {}", bucket);
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
