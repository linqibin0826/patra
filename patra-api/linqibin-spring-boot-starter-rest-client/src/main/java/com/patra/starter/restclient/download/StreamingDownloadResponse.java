package com.patra.starter.restclient.download;

import java.io.InputStream;

/// 流式下载响应。
///
/// 封装输入流及其元数据，支持 try-with-resources 自动关闭。
///
/// @param inputStream 输入流（调用方负责关闭）
/// @param contentLength 内容长度（-1 表示未知）
/// @param contentType 内容类型（可为空）
/// @author linqibin
/// @since 0.1.0
public record StreamingDownloadResponse(
    InputStream inputStream, long contentLength, String contentType) implements AutoCloseable {

  /// 创建流式下载结果。
  ///
  /// @param inputStream 输入流（不能为 null）
  /// @param contentLength 内容长度
  /// @param contentType 内容类型
  public StreamingDownloadResponse {
    if (inputStream == null) {
      throw new IllegalArgumentException("inputStream 不能为 null");
    }
  }

  /// 创建仅包含输入流的结果（元数据未知）。
  ///
  /// @param inputStream 输入流
  /// @return 流式下载响应
  public static StreamingDownloadResponse of(InputStream inputStream) {
    return new StreamingDownloadResponse(inputStream, -1, null);
  }

  @Override
  public void close() throws Exception {
    inputStream.close();
  }
}
