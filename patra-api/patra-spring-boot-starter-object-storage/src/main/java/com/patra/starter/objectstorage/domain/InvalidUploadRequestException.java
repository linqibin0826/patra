package com.patra.starter.objectstorage.domain;

/**
 * Signals that the upload request contains invalid arguments.
 *
 * <p>This exception indicates a permanent failure (client error) that should NOT be retried. It is
 * thrown when validation of request parameters (bucket, key, metadata) fails before attempting the
 * actual upload operation.
 *
 * @see UploadFailedException for transient upload failures that may be retried
 */
public class InvalidUploadRequestException extends UploadFailedException {

  /**
   * Constructs a new invalid upload request exception with the specified detail message.
   *
   * @param message the detail message explaining why the request is invalid
   */
  public InvalidUploadRequestException(String message) {
    super(message);
  }

  /**
   * Constructs a new invalid upload request exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public InvalidUploadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
