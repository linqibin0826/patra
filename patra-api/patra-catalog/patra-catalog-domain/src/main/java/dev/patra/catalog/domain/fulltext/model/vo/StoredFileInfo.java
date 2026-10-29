package dev.patra.catalog.domain.fulltext.model.vo;

/// 对象存储里一个全文文件的元信息。
///
/// @param size 字节数
/// @param contentType 内容类型
/// @param etag 存储返回的 ETag（不带引号）
/// @author linqibin
/// @since 0.11.0
public record StoredFileInfo(long size, String contentType, String etag) {

  /// 创建元信息。
  ///
  /// @param size 字节数
  /// @param contentType 内容类型
  /// @param etag ETag
  /// @return 元信息
  public static StoredFileInfo of(long size, String contentType, String etag) {
    return new StoredFileInfo(size, contentType, etag);
  }
}
