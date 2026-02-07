package com.patra.catalog.domain.port.source;

import java.nio.file.Path;

/// 文件下载结果。
///
/// 封装下载到本地的临时文件路径及大小。调用方负责在使用完毕后删除临时文件。
///
/// @param filePath 临时文件路径（调用方负责删除）
/// @param fileSize 文件大小（字节）
/// @author linqibin
/// @since 0.1.0
public record FileDownloadResult(Path filePath, long fileSize) {

  /// 紧凑构造器：参数验证。
  public FileDownloadResult {
    if (filePath == null) {
      throw new IllegalArgumentException("filePath 不能为 null");
    }
    if (fileSize < 0) {
      throw new IllegalArgumentException("fileSize 不能为负数");
    }
  }

  /// 创建文件下载结果。
  ///
  /// @param filePath 临时文件路径
  /// @param fileSize 文件大小（字节）
  /// @return 文件下载结果
  public static FileDownloadResult of(Path filePath, long fileSize) {
    return new FileDownloadResult(filePath, fileSize);
  }
}
