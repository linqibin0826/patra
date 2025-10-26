package com.patra.starter.objectstorage.domain;

/** Signals that uploading an object failed after retries. */
public class UploadFailedException extends RuntimeException {

  public UploadFailedException(String message) {
    super(message);
  }

  public UploadFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
