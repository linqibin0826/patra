package com.patra.starter.restclient.download.strategy;

import com.patra.starter.restclient.download.DownloadOptions;
import com.patra.starter.restclient.download.StreamingDownloadResponse;
import java.net.URI;

/// 流式下载策略接口。
///
/// 用于按协议选择不同的下载实现（HTTP/HTTPS/FTP 等）。
///
/// @author linqibin
/// @since 0.1.0
public interface StreamingDownloader {

  /// 判断是否支持当前 URL。
  ///
  /// @param url 下载地址
  /// @return 是否支持
  boolean supports(URI url);

  /// 打开流式下载输入流。
  ///
  /// @param url 下载地址
  /// @param options 下载选项（可为空）
  /// @return 流式下载结果
  StreamingDownloadResponse openStream(URI url, DownloadOptions options);
}
