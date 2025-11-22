package com.patra.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/// 封装 SHA-256 的共享哈希工具类,供各模块复用。
/// 
/// 行为与原始 starter-core 实现保持一致,以避免引入模块间依赖。
public final class HashUtils {
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private HashUtils() {}

  /// 计算提供的字符串的 SHA-256 摘要。
/// 
/// @param source 输入字符串;`null` 被视为空字符串
/// @return 原始 32 字节 SHA-256 摘要
  public static byte[] sha256(String source) {
    return sha256(source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8));
  }

  /// 计算提供的字节数组的 SHA-256 摘要。
/// 
/// @param source 输入字节;`null` 被视为空数组
/// @return 原始 32 字节 SHA-256 摘要
  public static byte[] sha256(byte[] source) {
    return digest("SHA-256", source == null ? new byte[0] : source);
  }

  /// 计算提供的字符串的 SHA-256 摘要并返回十六进制字符串。
/// 
/// @param source 输入字符串;`null` 被视为空字符串
/// @return 摘要的小写十六进制表示
  public static String sha256Hex(String source) {
    return sha256Hex(source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8));
  }

  /// 计算提供的字节数组的 SHA-256 摘要并返回十六进制字符串。
/// 
/// @param source 输入字节;`null` 被视为空数组
/// @return 摘要的小写十六进制表示
  public static String sha256Hex(byte[] source) {
    return toHex(sha256(source));
  }

  /// 将原始字节转换为小写十六进制字符串。
/// 
/// @param data 要转换的字节
/// @return 十六进制表示;如果输入为 `null` 或为空则返回空字符串
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

  /// 使用给定算法计算消息摘要。
/// 
/// @param algorithm 摘要算法名称
/// @param input 输入字节;`null` 被视为空数组
/// @return 摘要结果
  private static byte[] digest(String algorithm, byte[] input) {
    Objects.requireNonNull(algorithm, "算法");
    try {
      MessageDigest md = MessageDigest.getInstance(algorithm);
      return md.digest(input == null ? new byte[0] : input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("不支持的摘要算法: " + algorithm, e);
    }
  }
}
