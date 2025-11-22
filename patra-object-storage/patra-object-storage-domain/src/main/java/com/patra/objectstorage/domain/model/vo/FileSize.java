package com.patra.objectstorage.domain.model.vo;

import java.util.Locale;

/// 存储对象的物理字节大小。
///
/// 封装文件大小信息,确保大小值的有效性(非负),并提供人类可读的格式化输出功能。
///
/// @param bytes 对象占用的字节数(必须 >= 0)
public record FileSize(long bytes) {

  /// 规范构造器,强制执行文件大小的验证规则。
  ///
  /// 验证规则:
  ///
  /// - 字节数必须大于或等于0
  ///
  /// @throws IllegalArgumentException 如果字节数为负
  public FileSize {
    if (bytes < 0) {
      throw new IllegalArgumentException("文件大小必须 >= 0 字节");
    }
  }

  /// 将原始字节数转换为带单位的人类可读字符串。
  ///
  /// 根据文件大小自动选择合适的单位(B/KB/MB/GB),格式化为易读的文本表示。
  ///
  /// @return 人类友好的大小表示,例如 `1.23 MB`
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
