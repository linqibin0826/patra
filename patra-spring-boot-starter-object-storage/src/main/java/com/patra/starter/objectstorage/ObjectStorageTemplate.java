package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.InvalidUploadRequestException;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadFailedException;
import com.patra.starter.objectstorage.domain.UploadResult;
import com.patra.starter.objectstorage.metrics.ObjectStorageMetrics;
import java.io.InputStream;
import org.springframework.retry.support.RetryTemplate;

/** Template that wraps provider operations with retry and observability concerns. */
public class ObjectStorageTemplate implements ObjectStorageOperations {

  private final ObjectStorageProvider provider;
  private final RetryTemplate retryTemplate;
  private final ObjectStorageMetrics metrics;

  public ObjectStorageTemplate(
      ObjectStorageProvider provider, RetryTemplate retryTemplate, ObjectStorageMetrics metrics) {
    this.provider = provider;
    this.retryTemplate = retryTemplate;
    this.metrics = metrics;
  }

  @Override
  public UploadResult upload(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    return retryTemplate.execute(
        context -> {
          long start = System.nanoTime();
          try {
            UploadResult result = provider.upload(bucket, key, inputStream, metadata);
            metrics.recordUploadSuccess(
                provider.getProviderType(),
                bucket,
                System.nanoTime() - start,
                result.getFileSize());
            return result;
          } catch (Exception ex) {
            String errorType = classifyError(ex);
            metrics.recordUploadFailure(provider.getProviderType(), bucket, errorType);
            if (ex instanceof UploadFailedException uploadFailedException) {
              throw uploadFailedException;
            }
            throw new UploadFailedException("Upload failed", ex);
          } finally {
            if (context.getRetryCount() > 0) {
              metrics.recordRetry(provider.getProviderType(), bucket, context.getRetryCount());
            }
          }
        });
  }

  /**
   * Classify exception into error type for metrics tagging.
   *
   * @param ex the exception to classify
   * @return error type string: "validation", "network", "auth", or "unknown"
   */
  private String classifyError(Exception ex) {
    if (ex instanceof InvalidUploadRequestException) {
      return "validation";
    }

    // Check exception message for common patterns
    String message = ex.getMessage();
    if (message != null) {
      String lowerMessage = message.toLowerCase();
      if (lowerMessage.contains("auth") || lowerMessage.contains("credential")) {
        return "auth";
      }
      if (lowerMessage.contains("timeout")
          || lowerMessage.contains("connection")
          || lowerMessage.contains("network")) {
        return "network";
      }
    }

    // Check exception type
    if (ex instanceof java.io.IOException
        || ex instanceof java.net.SocketException
        || ex instanceof java.net.SocketTimeoutException) {
      return "network";
    }

    return "unknown";
  }
}
