package com.patra.starter.restclient.download;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.lang.Nullable;

/// 支持进度监控的下载客户端。
///
/// 提供带进度回调的文件下载能力，支持下载到指定路径或临时目录。
///
/// **使用示例**：
/// ```java
/// // 下载到指定路径
/// DownloadResult result = downloadClient.download(
///     URI.create("https://example.com/file.zip"),
///     Path.of("/tmp/file.zip"),
///     progress -> log.info("进度: {}%", progress.percentage())
/// );
///
/// // 下载到临时目录
/// DownloadResult result = downloadClient.downloadToTemp(
///     URI.create("https://example.com/file.zip"),
///     null  // 不需要进度监控
/// );
/// ```
///
/// **默认实现**：{@link DefaultDownloadClient}
///
/// @author linqibin
/// @since 0.1.0
/// @see DefaultDownloadClient
/// @see ProgressListener
public interface DownloadClient {

  /// 下载文件到指定路径。
  ///
  /// @param url 下载地址
  /// @param targetPath 目标文件路径（如果文件已存在将被覆盖）
  /// @param listener 进度监听器（可选，传 null 则不监控进度）
  /// @return 下载结果，包含文件路径、大小和最终进度
  /// @throws DownloadException 下载过程中发生错误（网络错误、IO 错误等）
  DownloadResult download(URI url, Path targetPath, @Nullable ProgressListener listener);

  /// 下载文件到系统临时目录。
  ///
  /// 临时文件命名格式：`download-{uuid}.tmp`
  ///
  /// @param url 下载地址
  /// @param listener 进度监听器（可选，传 null 则不监控进度）
  /// @return 下载结果，包含临时文件路径、大小和最终进度
  /// @throws DownloadException 下载过程中发生错误（网络错误、IO 错误等）
  DownloadResult downloadToTemp(URI url, @Nullable ProgressListener listener);

  /// 下载文件到指定路径（无进度监控）。
  ///
  /// 便捷方法，等价于 `download(url, targetPath, null)`。
  ///
  /// @param url 下载地址
  /// @param targetPath 目标文件路径
  /// @return 下载结果
  /// @throws DownloadException 下载过程中发生错误
  default DownloadResult download(URI url, Path targetPath) {
    return download(url, targetPath, null);
  }

  /// 下载文件到系统临时目录（无进度监控）。
  ///
  /// 便捷方法，等价于 `downloadToTemp(url, null)`。
  ///
  /// @param url 下载地址
  /// @return 下载结果
  /// @throws DownloadException 下载过程中发生错误
  default DownloadResult downloadToTemp(URI url) {
    return downloadToTemp(url, null);
  }
}
