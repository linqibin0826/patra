package com.patra.storage.domain.model.vo;

import java.util.Locale;

/** Captures message digests associated with a stored object for integrity validation. */
public record FileChecksum(String md5Hash, String sha256Hash) {

  /**
   * Creates a checksum ensuring at least one hash is provided.
   *
   * @param md5Hash optional MD5 digest (hex encoded)
   * @param sha256Hash optional SHA-256 digest (hex encoded)
   */
  public FileChecksum {
    if ((isBlank(md5Hash) && isBlank(sha256Hash))) {
      throw new IllegalArgumentException("Either MD5 or SHA-256 hash must be provided");
    }
    md5Hash = normalize(md5Hash);
    sha256Hash = normalize(sha256Hash);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ENGLISH);
  }
}
