package com.patra.catalog.domain.port.source;

import java.io.InputStream;

/// 流式下载结果。
///
/// 封装 HTTP 响应体输入流及其元数据，支持 try-with-resources 自动资源管理。
///
/// **使用示例**：
///
/// ```java
/// try (StreamingDownloadResult result = port.download(uri)) {
///     parserPort.parse(result.inputStream())...
/// }
/// ```
///
/// @param inputStream HTTP 响应体输入流（调用方负责关闭）
/// @param contentLength 内容长度（-1 表示未知，如 chunked 编码）
/// @param contentType 内容类型（可为 null）
/// @author linqibin
/// @since 0.1.0
public record StreamingDownloadResult(
    InputStream inputStream, long contentLength, String contentType) implements AutoCloseable {

  /// 创建流式下载结果。
  ///
  /// @param inputStream HTTP 响应体输入流（不能为 null）
  /// @param contentLength 内容长度（-1 表示未知）
  /// @param contentType 内容类型（可为 null）
  public StreamingDownloadResult {
    if (inputStream == null) {
      throw new IllegalArgumentException("inputStream 不能为 null");
    }
  }

  /// 创建仅包含输入流的结果（元数据未知）。
  ///
  /// @param inputStream HTTP 响应体输入流
  /// @return 流式下载结果
  public static StreamingDownloadResult of(InputStream inputStream) {
    return new StreamingDownloadResult(inputStream, -1, null);
  }

  @Override
  public void close() throws Exception {
    if (inputStream != null) {
      inputStream.close();
    }
  }
}
