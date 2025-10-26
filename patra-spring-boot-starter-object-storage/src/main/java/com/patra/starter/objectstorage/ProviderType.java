package com.patra.starter.objectstorage;

/** Supported object storage providers. */
public enum ProviderType {
  MINIO,
  S3;

  public static ProviderType from(String provider) {
    if (provider == null || provider.isBlank()) {
      return MINIO;
    }
    for (ProviderType type : values()) {
      if (type.name().equalsIgnoreCase(provider)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unsupported provider type: " + provider);
  }
}
