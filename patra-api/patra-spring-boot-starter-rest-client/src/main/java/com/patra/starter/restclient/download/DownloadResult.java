package com.patra.starter.restclient.download;

import java.nio.file.Path;

/// 下载结果。
///
/// 包含下载完成后的文件信息和最终进度数据。
///
/// @param filePath 下载文件的路径
/// @param fileSize 文件大小（字节）
/// @param finalProgress 最终进度信息，包含总耗时、速度等
/// @author linqibin
/// @since 0.1.0
public record DownloadResult(Path filePath, long fileSize, DownloadProgress finalProgress) {

  /// 获取下载耗时（毫秒）。
  ///
  /// @return 下载总耗时
  public long durationMillis() {
    return finalProgress.elapsedMillis();
  }

  /// 获取平均下载速度（字节/秒）。
  ///
  /// @return 平均速度
  public long averageSpeedBytesPerSecond() {
    long duration = finalProgress.elapsedMillis();
    return duration > 0 ? (fileSize * 1000) / duration : 0;
  }
}
