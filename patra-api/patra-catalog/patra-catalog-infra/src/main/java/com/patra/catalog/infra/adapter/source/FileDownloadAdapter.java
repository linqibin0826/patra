package com.patra.catalog.infra.adapter.source;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.starter.restclient.download.DownloadClient;
import com.patra.starter.restclient.download.DownloadException;
import com.patra.starter.restclient.download.DownloadResult;
import com.patra.starter.restclient.download.ProgressListener;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/// 文件下载端口适配器。
///
/// 使用 DownloadClient 下载远程文件，支持进度监控。
///
/// **进度监控**：自动注入的 ProgressListener 会：
/// - 每 10% 输出日志（已下载/总大小、速度、剩余时间）
/// - 记录 Micrometer 指标（耗时分布、下载量、成功/失败次数）
///
/// **超时配置**：使用 longRunningRestClient（默认 10 分钟读取超时），
/// 适合大文件下载场景，可通过 `patra.rest-client.clients.long-running.timeout` 配置调整。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class FileDownloadAdapter implements FileDownloadPort {

  private final DownloadClient downloadClient;
  private final ProgressListener progressListener;

  /// 构造文件下载适配器。
  ///
  /// @param downloadClient 下载客户端
  /// @param progressListener 进度监听器（可选，为 null 则不监控进度）
  public FileDownloadAdapter(
      DownloadClient downloadClient, @Nullable ProgressListener progressListener) {
    this.downloadClient = downloadClient;
    this.progressListener = progressListener;
  }

  @Override
  public Path downloadToTemp(URI url) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");
    log.info("开始下载文件：{}", url);

    try {
      DownloadResult result = downloadClient.downloadToTemp(url, progressListener);

      log.info(
          "文件下载完成：{}，大小：{} bytes，耗时：{}ms",
          result.filePath(),
          result.fileSize(),
          result.durationMillis());

      return result.filePath();

    } catch (DownloadException e) {
      // 将 starter 层的异常转换为 domain 层异常
      throw new FileDownloadException(
          e.getMessage(),
          e,
          e.getErrorTraits().toArray(new com.patra.common.error.trait.ErrorTrait[0]));
    }
  }
}
