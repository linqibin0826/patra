package com.patra.storage.domain.model.vo;

import java.util.Locale;

/** Represents the physical byte size of a stored object. */
public record FileSize(long bytes) {

  /**
   * Creates a new file size ensuring a non-negative quantity.
   *
   * @param bytes number of bytes occupied by the object (>= 0)
   */
  public FileSize {
    if (bytes < 0) {
      throw new IllegalArgumentException("File size must be >= 0 bytes");
    }
  }

  /**
   * Converts the raw byte count into a human readable string with units.
   *
   * @return human friendly size, e.g., {@code 1.23 MB}
   */
  public String humanReadable() {
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024 * 1024) {
      return String.format(Locale.ENGLISH, "%.2f KB", bytes / 1024.0);
    }
    if (bytes < 1024L * 1024 * 1024) {
      return String.format(Locale.ENGLISH, "%.2f MB", bytes / (1024.0 * 1024));
    }
    return String.format(Locale.ENGLISH, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
  }
}
