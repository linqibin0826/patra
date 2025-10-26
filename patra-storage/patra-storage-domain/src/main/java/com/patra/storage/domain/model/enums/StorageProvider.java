package com.patra.storage.domain.model.enums;

import java.util.Arrays;

/** Enumerates object storage providers supported by the platform. */
public enum StorageProvider {
  MINIO,
  S3,
  OSS,
  COS;

  /**
   * Resolves a provider by (case-insensitive) name.
   *
   * @param value textual provider representation supplied by callers
   * @return matching {@link StorageProvider}
   * @throws IllegalArgumentException if the value is unknown
   */
  public static StorageProvider fromName(String value) {
    return Arrays.stream(values())
        .filter(provider -> provider.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported storage provider: " + value));
  }
}
