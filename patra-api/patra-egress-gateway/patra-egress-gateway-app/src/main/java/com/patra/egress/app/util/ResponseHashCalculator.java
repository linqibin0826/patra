package com.patra.egress.app.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility class for calculating response body hash using SHA-256
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class ResponseHashCalculator {

  private ResponseHashCalculator() {
    // Utility class, prevent instantiation
  }

  /**
   * Calculate SHA-256 hash of response body
   *
   * @param body response body
   * @return hex-encoded hash string
   */
  public static String calculateHash(String body) {
    if (body == null || body.isEmpty()) {
      return "";
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }
}
