package com.patra.starter.restclient.download;

import java.math.BigDecimal;
import java.math.RoundingMode;

/// 下载进度信息。
///
/// 包含下载过程中的所有状态数据，支持进度追踪和速度计算。
///
/// **字段说明**：
/// - `bytesDownloaded`：已下载的字节数
/// - `totalBytes`：文件总大小（-1 表示服务器未返回 Content-Length）
/// - `percentage`：进度百分比（0-100，totalBytes 未知时为 -1）
/// - `speedBytesPerSecond`：当前下载速度（字节/秒），基于最近一次更新计算
/// - `estimatedRemainingSeconds`：预估剩余时间（秒，-1 表示无法预估）
/// - `elapsedMillis`：从开始下载到现在的总耗时（毫秒）
///
/// @param bytesDownloaded 已下载字节数
/// @param totalBytes 总字节数（-1 表示未知）
/// @param percentage 进度百分比（0-100，totalBytes 未知时为 -1）
/// @param speedBytesPerSecond 当前下载速度（字节/秒）
/// @param estimatedRemainingSeconds 预估剩余时间（秒，-1 表示无法预估）
/// @param elapsedMillis 已用时间（毫秒）
/// @author linqibin
/// @since 0.1.0
public record DownloadProgress(
    long bytesDownloaded,
    long totalBytes,
    int percentage,
    long speedBytesPerSecond,
    long estimatedRemainingSeconds,
    long elapsedMillis) {

  private static final long KB = 1024L;
  private static final long MB = KB * 1024;
  private static final long GB = MB * 1024;

  /// 格式化速度为人类可读形式。
  ///
  /// **输出示例**：
  /// - `"12.5 MB/s"`
  /// - `"956.3 KB/s"`
  /// - `"1.2 GB/s"`
  ///
  /// @return 格式化的速度字符串
  public String formattedSpeed() {
    return formatBytes(speedBytesPerSecond) + "/s";
  }

  /// 格式化剩余时间为人类可读形式。
  ///
  /// **输出示例**：
  /// - `"2m 30s"`
  /// - `"1h 15m"`
  /// - `"45s"`
  /// - `"未知"`（当无法预估时）
  ///
  /// @return 格式化的剩余时间字符串
  public String formattedRemainingTime() {
    if (estimatedRemainingSeconds < 0) {
      return "未知";
    }

    long seconds = estimatedRemainingSeconds;
    if (seconds < 60) {
      return seconds + "s";
    }

    long minutes = seconds / 60;
    long remainingSeconds = seconds % 60;
    if (minutes < 60) {
      return remainingSeconds > 0 ? minutes + "m " + remainingSeconds + "s" : minutes + "m";
    }

    long hours = minutes / 60;
    long remainingMinutes = minutes % 60;
    return remainingMinutes > 0 ? hours + "h " + remainingMinutes + "m" : hours + "h";
  }

  /// 格式化已下载大小为人类可读形式。
  ///
  /// **输出示例**：
  /// - `"156.3 MB / 300.0 MB (52%)"` - 已知总大小
  /// - `"156.3 MB / 未知"` - 未知总大小
  ///
  /// @return 格式化的大小字符串
  public String formattedSize() {
    String downloaded = formatBytes(bytesDownloaded);
    if (totalBytes > 0) {
      String total = formatBytes(totalBytes);
      return downloaded + " / " + total + " (" + percentage + "%)";
    }
    return downloaded + " / 未知";
  }

  /// 格式化已用时间为人类可读形式。
  ///
  /// **输出示例**：
  /// - `"98.5s"`
  /// - `"2m 30s"`
  ///
  /// @return 格式化的已用时间字符串
  public String formattedElapsedTime() {
    long seconds = elapsedMillis / 1000;
    long millis = elapsedMillis % 1000;

    if (seconds < 60) {
      return formatValue(seconds + millis / 1000.0) + "s";
    }

    long minutes = seconds / 60;
    long remainingSeconds = seconds % 60;
    if (minutes < 60) {
      return minutes + "m " + remainingSeconds + "s";
    }

    long hours = minutes / 60;
    long remainingMinutes = minutes % 60;
    return hours + "h " + remainingMinutes + "m";
  }

  /// 判断总大小是否已知。
  ///
  /// @return 如果服务器返回了 Content-Length 则为 true
  public boolean isTotalBytesKnown() {
    return totalBytes > 0;
  }

  /// 判断下载是否完成。
  ///
  /// @return 如果已下载字节数等于总字节数则为 true
  public boolean isComplete() {
    return totalBytes > 0 && bytesDownloaded >= totalBytes;
  }

  private static String formatBytes(long bytes) {
    if (bytes >= GB) {
      return formatValue((double) bytes / GB) + " GB";
    } else if (bytes >= MB) {
      return formatValue((double) bytes / MB) + " MB";
    } else if (bytes >= KB) {
      return formatValue((double) bytes / KB) + " KB";
    } else {
      return bytes + " B";
    }
  }

  /// 使用 BigDecimal 格式化数值（线程安全）。
  ///
  /// 保留最多两位小数，自动去除尾部零。
  ///
  /// @param value 要格式化的数值
  /// @return 格式化后的字符串
  private static String formatValue(double value) {
    return BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString();
  }
}
