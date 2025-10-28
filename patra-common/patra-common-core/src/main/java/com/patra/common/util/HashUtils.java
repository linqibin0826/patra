package com.patra.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Shared hashing utilities currently wrapping SHA-256 for reuse across modules.
 *
 * <p>Behavior mirrors the original starter-core implementation to avoid introducing inter-module
 * dependencies.
 */
public final class HashUtils {
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private HashUtils() {}

  /**
   * Computes a SHA-256 digest for the provided string.
   *
   * @param source input string; {@code null} is treated as an empty string
   * @return raw 32-byte SHA-256 digest
   */
  public static byte[] sha256(String source) {
    return sha256(source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Computes a SHA-256 digest for the provided byte array.
   *
   * @param source input bytes; {@code null} is treated as an empty array
   * @return raw 32-byte SHA-256 digest
   */
  public static byte[] sha256(byte[] source) {
    return digest("SHA-256", source == null ? new byte[0] : source);
  }

  /**
   * Computes a SHA-256 digest for the provided string and returns a hex string.
   *
   * @param source input string; {@code null} is treated as an empty string
   * @return lowercase hexadecimal representation of the digest
   */
  public static String sha256Hex(String source) {
    return sha256Hex(source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Computes a SHA-256 digest for the provided byte array and returns a hex string.
   *
   * @param source input bytes; {@code null} is treated as an empty array
   * @return lowercase hexadecimal representation of the digest
   */
  public static String sha256Hex(byte[] source) {
    return toHex(sha256(source));
  }

  /**
   * Converts raw bytes into a lowercase hexadecimal string.
   *
   * @param data bytes to convert
   * @return hexadecimal representation; empty string if the input is {@code null} or empty
   */
  public static String toHex(byte[] data) {
    if (data == null || data.length == 0) {
      return "";
    }
    char[] chars = new char[data.length * 2];
    for (int i = 0; i < data.length; i++) {
      int v = data[i] & 0xFF;
      chars[i * 2] = HEX_DIGITS[v >>> 4];
      chars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
    }
    return new String(chars);
  }

  /**
   * Computes a message digest with the given algorithm.
   *
   * @param algorithm digest algorithm name
   * @param input input bytes; {@code null} is treated as an empty array
   * @return digest result
   */
  private static byte[] digest(String algorithm, byte[] input) {
    Objects.requireNonNull(algorithm, "algorithm");
    try {
      MessageDigest md = MessageDigest.getInstance(algorithm);
      return md.digest(input == null ? new byte[0] : input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unsupported digest algorithm: " + algorithm, e);
    }
  }
}
