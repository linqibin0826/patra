package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.InvalidUploadRequestException;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadFailedException;
import com.patra.starter.objectstorage.domain.UploadResult;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/** MinIO implementation of {@link ObjectStorageProvider}. */
@Slf4j
public class MinioStorageProvider implements ObjectStorageProvider {

  /**
   * Bucket name validation pattern following S3 naming rules.
   *
   * <p>Rules: 3-63 characters, lowercase letters/numbers/dots/hyphens, must start/end with
   * letter/number.
   */
  private static final Pattern BUCKET_NAME_PATTERN =
      Pattern.compile("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$");

  /** Maximum length for object keys (S3 compatibility limit). */
  private static final int MAX_KEY_LENGTH = 1024;

  private final MinioClient minioClient;
  private final long maxFileSize;

  /**
   * Local cache of known bucket names to avoid redundant existence checks.
   *
   * <p>This cache improves performance for high-frequency uploads to the same bucket by skipping
   * network calls to {@link MinioClient#bucketExists}. If a bucket is deleted externally, the cache
   * becomes stale, but subsequent upload attempts will fail gracefully with clear error messages.
   */
  private final Set<String> knownBuckets = ConcurrentHashMap.newKeySet();

  /**
   * Constructs a new MinIO storage provider.
   *
   * @param minioClient configured MinIO client
   * @param maxFileSize maximum allowed file size in bytes
   */
  public MinioStorageProvider(MinioClient minioClient, long maxFileSize) {
    this.minioClient = minioClient;
    this.maxFileSize = maxFileSize;
  }

  @Override
  public ProviderType getProviderType() {
    return ProviderType.MINIO;
  }

  /**
   * Upload an object to MinIO storage.
   *
   * <p>This method ensures the target bucket exists before uploading. If the bucket does not exist,
   * it will be created automatically.
   *
   * <p><b>Resource Management:</b> The MinIO SDK consumes the {@code inputStream} and closes it
   * internally. The caller should not attempt to reuse or close the stream after this method
   * returns.
   *
   * @param bucket bucket name (will be created if not exists)
   * @param key unique object key within the bucket
   * @param inputStream content to upload (will be closed by MinIO SDK)
   * @param metadata file metadata including size and content type
   * @return upload result with storage key and etag
   * @throws InvalidUploadRequestException if arguments are invalid (not retryable)
   * @throws UploadFailedException if upload fails due to network or server errors (retryable)
   */
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

      UploadResult result =
          UploadResult.builder()
              .storageKey(bucket + "/" + key)
              .bucketName(bucket)
              .objectKey(key)
              .etag(response.etag())
              .fileSize(metadata.getContentLength())
              .build();

      log.info(
          "File uploaded successfully to MinIO: bucket={}, key={}, size={} bytes, etag={}",
          bucket,
          key,
          metadata.getContentLength(),
          response.etag());

      return result;

    } catch (InvalidUploadRequestException ex) {
      // Re-throw validation errors without wrapping (should not be retried)
      throw ex;
    } catch (Exception ex) {
      log.error(
          "MinIO upload failed: bucket={}, key={}, size={} bytes",
          bucket,
          key,
          metadata.getContentLength(),
          ex);
      throw new UploadFailedException(
          String.format("MinIO upload failed for bucket=%s, key=%s", bucket, key), ex);
    }
  }

  /**
   * Ensures the specified bucket exists, creating it if necessary.
   *
   * <p>This method uses a local cache to avoid redundant network calls for frequently accessed
   * buckets. On first access, it checks MinIO and caches the result.
   *
   * @param bucket the bucket name to check/create
   * @throws Exception if bucket check or creation fails
   */
  private void ensureBucketExists(String bucket) throws Exception {
    // Check cache first to avoid network call
    if (knownBuckets.contains(bucket)) {
      return;
    }

    // Cache miss - check MinIO
    boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      log.info("Created MinIO bucket: {}", bucket);
    }

    // Add to cache for future uploads
    knownBuckets.add(bucket);
  }

  /**
   * Validates upload arguments for null/empty checks, format compliance, and size limits.
   *
   * @param bucket bucket name
   * @param key object key
   * @param inputStream content stream
   * @param metadata file metadata
   * @throws InvalidUploadRequestException if any validation fails
   */
  private void validateArguments(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    // Null/empty checks
    if (bucket == null || bucket.isBlank()) {
      throw new InvalidUploadRequestException("Bucket name must be provided and not empty");
    }
    if (key == null || key.isBlank()) {
      throw new InvalidUploadRequestException("Object key must be provided and not empty");
    }
    if (inputStream == null) {
      throw new InvalidUploadRequestException("Input stream cannot be null");
    }
    if (metadata == null) {
      throw new InvalidUploadRequestException("Object metadata is required");
    }
    if (metadata.getContentLength() <= 0) {
      throw new InvalidUploadRequestException(
          String.format(
              "Content length must be greater than 0, got %d", metadata.getContentLength()));
    }

    // Bucket format validation
    if (bucket.length() < 3 || bucket.length() > 63) {
      throw new InvalidUploadRequestException(
          String.format(
              "Bucket name length must be between 3 and 63 characters, got %d", bucket.length()));
    }
    if (!BUCKET_NAME_PATTERN.matcher(bucket).matches()) {
      throw new InvalidUploadRequestException(
          String.format(
              "Bucket name '%s' is invalid. Must contain only lowercase letters, numbers, dots,"
                  + " and hyphens, and must start/end with a letter or number",
              bucket));
    }
    if (bucket.contains("..")) {
      throw new InvalidUploadRequestException(
          String.format(
              "Bucket name '%s' contains consecutive dots, which is not allowed", bucket));
    }

    // Object key format validation
    if (key.length() > MAX_KEY_LENGTH) {
      throw new InvalidUploadRequestException(
          String.format(
              "Object key length exceeds maximum of %d characters, got %d",
              MAX_KEY_LENGTH, key.length()));
    }
    if (key.startsWith("/")) {
      throw new InvalidUploadRequestException(
          String.format("Object key '%s' cannot start with a forward slash", key));
    }
    if (key.contains("//")) {
      throw new InvalidUploadRequestException(
          String.format("Object key '%s' contains consecutive slashes, which is not allowed", key));
    }

    // File size validation
    if (metadata.getContentLength() > maxFileSize) {
      throw new InvalidUploadRequestException(
          String.format(
              "File size %d bytes exceeds maximum allowed size of %d bytes (%.2f MB)",
              metadata.getContentLength(), maxFileSize, maxFileSize / 1024.0 / 1024.0));
    }
  }
}
