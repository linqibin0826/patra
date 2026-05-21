package dev.linqibin.starter.restclient.download;

import java.net.URI;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/// 下载请求。
///
/// 用于描述一次落盘下载的核心参数。
///
/// @param url 下载地址（必填）
/// @param targetPath 目标路径（可为空，表示使用默认目录）
/// @param options 下载选项（可为空，表示使用默认配置）
/// @author linqibin
/// @since 0.1.0
public record DownloadRequest(
    URI url, @Nullable Path targetPath, @Nullable DownloadOptions options) {

  /// 创建下载请求。
  ///
  /// @param url 下载地址
  /// @param targetPath 目标路径
  /// @return 下载请求
  public static DownloadRequest of(URI url, Path targetPath) {
    return new DownloadRequest(url, targetPath, null);
  }
}
