package dev.linqibin.commons.json;

/// JSON 规范化结果容器。
///
/// 提供对规范值(结构化 Java 对象)、规范 JSON 文本(紧凑字符串表示)以及适用于哈希或签名的 UTF-8 字节的访问。
public final class JsonNormalizerResult {
  private final Object canonicalValue;
  private final String canonicalJson;
  private final byte[] hashMaterial;

  JsonNormalizerResult(Object canonicalValue, String canonicalJson, byte[] hashMaterial) {
    this.canonicalValue = canonicalValue;
    this.canonicalJson = canonicalJson;
    this.hashMaterial = hashMaterial;
  }

  /// 返回作为结构化 Java 对象的规范值。
  ///
  /// @return 规范值(Map、List、String、Number、Boolean 或 null)
  public Object getCanonicalValue() {
    return canonicalValue;
  }

  /// 返回作为紧凑字符串的规范 JSON。
  ///
  /// @return 规范 JSON 字符串
  public String getCanonicalJson() {
    return canonicalJson;
  }

  /// 返回规范 JSON 的 UTF-8 字节,用于哈希或签名。
  ///
  /// @return UTF-8 字节数组
  public byte[] getHashMaterial() {
    return hashMaterial;
  }
}
