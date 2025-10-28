package com.patra.common.json;

/**
 * Container for JSON normalization results.
 *
 * <p>Provides access to the canonical value (structured Java object), canonical JSON text (compact
 * string representation), and UTF-8 bytes suitable for hashing or signing.
 */
public final class JsonNormalizerResult {
  private final Object canonicalValue;
  private final String canonicalJson;
  private final byte[] hashMaterial;

  JsonNormalizerResult(Object canonicalValue, String canonicalJson, byte[] hashMaterial) {
    this.canonicalValue = canonicalValue;
    this.canonicalJson = canonicalJson;
    this.hashMaterial = hashMaterial;
  }

  /**
   * Returns the canonical value as a structured Java object.
   *
   * @return canonical value (Map, List, String, Number, Boolean, or null)
   */
  public Object getCanonicalValue() {
    return canonicalValue;
  }

  /**
   * Returns the canonical JSON as a compact string.
   *
   * @return canonical JSON string
   */
  public String getCanonicalJson() {
    return canonicalJson;
  }

  /**
   * Returns UTF-8 bytes of the canonical JSON for hashing or signing.
   *
   * @return UTF-8 byte array
   */
  public byte[] getHashMaterial() {
    return hashMaterial;
  }
}
