package com.patra.objectstorage.domain.model.vo;

import java.util.Locale;

/// 存储对象关联的消息摘要,用于完整性验证。
///
/// 封装文件的哈希校验值,支持MD5和SHA-256算法。至少需要提供一个哈希值,用于验证文件传输和存储过程中的完整性。 哈希值在存储前会被标准化为小写形式。
///
/// @param md5Hash 可选的MD5摘要(十六进制编码)
/// @param sha256Hash 可选的SHA-256摘要(十六进制编码)
public record FileChecksum(String md5Hash, String sha256Hash) {

  /// 规范构造器,强制执行校验和的验证规则。
  ///
  /// 验证规则:
  ///
  /// - 至少提供MD5或SHA-256哈希值之一
  ///   - 哈希值会被标准化为小写形式
  ///
  /// @throws IllegalArgumentException 如果MD5和SHA-256哈希都未提供
  public FileChecksum {
    if ((isBlank(md5Hash) && isBlank(sha256Hash))) {
      throw new IllegalArgumentException("必须提供 MD5 或 SHA-256 哈希值");
    }
    md5Hash = normalize(md5Hash);
    sha256Hash = normalize(sha256Hash);
  }

  /// 检查字符串是否为空白。
  ///
  /// @param value 待检查的字符串
  /// @return 如果字符串为null或空白则返回true
  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /// 标准化哈希值为小写形式。
  ///
  /// @param value 原始哈希值
  /// @return 小写的哈希值,如果输入为null则返回null
  private static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ENGLISH);
  }
}
