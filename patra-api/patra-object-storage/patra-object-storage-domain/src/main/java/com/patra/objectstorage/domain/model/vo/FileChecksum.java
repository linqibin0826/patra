package com.patra.objectstorage.domain.model.vo;

import java.util.Locale;

/// 存储对象关联的消息摘要,用于完整性验证。
/// 
/// 封装文件的哈希校验值,支持MD5和SHA-256算法。至少需要提供一个哈希值,用于验证文件传输和存储过程中的完整性。 哈希值在存储前会被标准化为小写形式。
/// 
/// @param md5Hash 可选的MD5摘要(十六进制编码)
/// @param sha256Hash 可选的SHA-256摘要(十六进制编码)
public record FileChecksum(String md5Hash, String sha256Hash) {

  /// 创建校验和,确保至少提供一个哈希值。
/// 
/// @throws IllegalArgumentException 如果MD5和SHA-256哈希都未提供
  public FileChecksum {
    if ((isBlank(md5Hash) && isBlank(sha256Hash))) {
      throw new IllegalArgumentException("必须提供 MD5 或 SHA-256 哈希值");
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
