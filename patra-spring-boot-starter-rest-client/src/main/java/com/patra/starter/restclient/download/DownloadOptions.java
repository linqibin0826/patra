package com.patra.starter.restclient.download;

import org.springframework.lang.Nullable;

/// 下载选项。
///
/// 用于覆盖默认配置的可选参数，允许调用方按需指定写入策略、缓冲区大小、FTP 账号等。
///
/// @param writeStrategy 写入策略（可空，使用默认配置）
/// @param createDirs 是否自动创建目录（可空，使用默认配置）
/// @param cleanupOnFailure 下载失败时是否清理文件（可空，使用默认配置）
/// @param bufferSize 读写缓冲区大小（可空，使用默认配置）
/// @param ftpCredentials FTP 用户名密码（可空，使用默认配置）
/// @param progressListener 进度监听器（可空，不监听）
/// @author linqibin
/// @since 0.1.0
public record DownloadOptions(
    @Nullable WriteStrategy writeStrategy,
    @Nullable Boolean createDirs,
    @Nullable Boolean cleanupOnFailure,
    @Nullable Integer bufferSize,
    @Nullable FtpCredentials ftpCredentials,
    @Nullable ProgressListener progressListener) {

  /// 创建默认下载选项（全部为 null，表示使用配置默认值）。
  ///
  /// @return 默认下载选项
  public static DownloadOptions defaultOptions() {
    return new DownloadOptions(null, null, null, null, null, null);
  }

  /// 使用进度监听器创建下载选项。
  ///
  /// @param listener 进度监听器
  /// @return 下载选项
  public static DownloadOptions withProgressListener(ProgressListener listener) {
    return new DownloadOptions(null, null, null, null, null, listener);
  }
}
