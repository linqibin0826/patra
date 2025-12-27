package com.patra.starter.restclient.download;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.lang.Nullable;

/// 统一下载客户端（流式 + 落盘）。
///
/// 提供两类能力：
/// - **流式下载**：返回 `InputStream` 供调用方自行消费
/// - **落盘下载**：根据策略将文件保存到本地路径
///
/// **使用示例**：
/// ```java
/// // 流式下载
/// try (StreamingDownloadResponse result =
///     downloadClient.openStream(URI.create("https://example.com/a.xml"), null)) {
///   String content = new String(result.inputStream().readAllBytes(), StandardCharsets.UTF_8);
/// }
///
/// // 落盘下载
/// DownloadResult result =
///     downloadClient.download(
///         new DownloadRequest(
///             URI.create("https://example.com/file.zip"),
///             Path.of("/tmp/file.zip"),
///             DownloadOptions.defaultOptions()));
/// ```
///
/// **默认实现**：{@link DefaultDownloadClient}
///
/// @author linqibin
/// @since 0.1.0
/// @see DefaultDownloadClient
/// @see ProgressListener
public interface DownloadClient {

  /// 打开远程资源的流式下载输入流。
  ///
  /// 调用方必须在使用完毕后关闭返回的结果（建议 try-with-resources）。
  ///
  /// @param url 下载地址
  /// @param options 下载选项（可选，传 null 使用默认配置）
  /// @return 流式下载结果
  /// @throws DownloadException 下载过程中发生错误（网络错误、IO 错误等）
  StreamingDownloadResponse openStream(URI url, @Nullable DownloadOptions options);

  /// 打开远程资源的流式下载输入流（使用默认选项）。
  ///
  /// @param url 下载地址
  /// @return 流式下载结果
  default StreamingDownloadResponse openStream(URI url) {
    return openStream(url, null);
  }

  /// 下载文件到指定路径或默认目录。
  ///
  /// @param request 下载请求
  /// @return 下载结果，包含文件路径、大小和最终进度
  /// @throws DownloadException 下载过程中发生错误（网络错误、IO 错误等）
  DownloadResult download(DownloadRequest request);

  /// 下载文件到系统临时目录。
  ///
  /// 临时文件命名格式：`download-{uuid}.tmp`
  ///
  /// @param url 下载地址
  /// @param options 下载选项（可选，传 null 使用默认配置）
  /// @return 下载结果，包含临时文件路径、大小和最终进度
  /// @throws DownloadException 下载过程中发生错误（网络错误、IO 错误等）
  DownloadResult downloadToTemp(URI url, @Nullable DownloadOptions options);

  /// 下载文件到指定路径（便捷方法）。
  ///
  /// @param url 下载地址
  /// @param targetPath 目标文件路径
  /// @return 下载结果
  default DownloadResult download(URI url, Path targetPath) {
    return download(new DownloadRequest(url, targetPath, null));
  }

  /// 下载文件到默认目录（使用全局配置）。
  ///
  /// @param url 下载地址
  /// @return 下载结果
  default DownloadResult download(URI url) {
    return download(new DownloadRequest(url, null, null));
  }

  /// 下载文件到指定路径（带选项）。
  ///
  /// @param url 下载地址
  /// @param targetPath 目标文件路径
  /// @param options 下载选项（可选）
  /// @return 下载结果
  default DownloadResult download(URI url, Path targetPath, @Nullable DownloadOptions options) {
    return download(new DownloadRequest(url, targetPath, options));
  }

  /// 下载文件到系统临时目录（便捷方法）。
  ///
  /// @param url 下载地址
  /// @return 下载结果
  default DownloadResult downloadToTemp(URI url) {
    return downloadToTemp(url, null);
  }
}
