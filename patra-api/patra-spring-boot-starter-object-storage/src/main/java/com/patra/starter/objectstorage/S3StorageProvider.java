package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.InvalidUploadRequestException;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadFailedException;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/** AWS S3 implementation of {@link ObjectStorageProvider}. */
@Slf4j
public class S3StorageProvider implements ObjectStorageProvider {

  /**
   * Bucket name validation pattern following S3 naming rules.
   *
   * <p>Rules: 3-63 characters, lowercase letters/numbers/dots/hyphens, must start/end with
   * letter/number.
   */
  private static final Pattern BUCKET_NAME_PATTERN =
      Pattern.compile("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$");

  /** Maximum length for object keys (S3 limit). */
  private static final int MAX_KEY_LENGTH = 1024;

  private final S3Client s3Client;
  private final long maxFileSize;

  /**
   * Constructs a new S3 storage provider.
   *
   * @param s3Client configured S3 client
   * @param maxFileSize maximum allowed file size in bytes
   */
  public S3StorageProvider(S3Client s3Client, long maxFileSize) {
    this.s3Client = s3Client;
    this.maxFileSize = maxFileSize;
  }

  @Override
  public ProviderType getProviderType() {
    return ProviderType.S3;
  }

  /**
   * Upload an object to AWS S3 storage.
   *
   * <p>Unlike MinIO, S3 does not automatically create buckets. The target bucket must exist before
   * calling this method, otherwise an exception will be thrown.
   *
   * <p><b>Resource Management:</b> The AWS SDK consumes the {@code inputStream} and closes it
   * internally. The caller should not attempt to reuse or close the stream after this method
   * returns.
   *
   * @param bucket bucket name (must already exist)
   * @param key unique object key within the bucket
   * @param inputStream content to upload (will be closed by AWS SDK)
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

      UploadResult result =
          UploadResult.builder()
              .storageKey(bucket + "/" + key)
              .bucketName(bucket)
              .objectKey(key)
              .etag(response.eTag())
              .fileSize(metadata.getContentLength())
              .build();

      log.info(
          "File uploaded successfully to S3: bucket={}, key={}, size={} bytes, etag={}",
          bucket,
          key,
          metadata.getContentLength(),
          response.eTag());

      return result;

    } catch (InvalidUploadRequestException ex) {
      // Re-throw validation errors without wrapping (should not be retried)
      throw ex;
    } catch (Exception ex) {
      log.error(
          "S3 upload failed: bucket={}, key={}, size={} bytes",
          bucket,
          key,
          metadata.getContentLength(),
          ex);
      throw new UploadFailedException(
          String.format("S3 upload failed for bucket=%s, key=%s", bucket, key), ex);
    }
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
