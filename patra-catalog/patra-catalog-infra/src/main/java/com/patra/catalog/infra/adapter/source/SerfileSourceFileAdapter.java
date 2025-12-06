package com.patra.catalog.infra.adapter.source;

import com.patra.catalog.domain.exception.SerfileDownloadException;
import com.patra.catalog.domain.port.source.SerfileSourceFilePort;
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

/// Serfile 数据源文件适配器。
///
/// 使用 {@link DownloadClient} 从 NLM 服务器下载 SerfileBase XML 文件。
/// 支持进度监控和大文件下载。
///
/// **NLM 数据源**：
///
/// - 主要：`https://ftp.ncbi.nlm.nih.gov/pubmed/Serfile/serfilebase2025.xml`
/// - 备选：`https://nlmpubs.nlm.nih.gov/projects/serials/Serfiles/serfilebase2025.xml`
///
/// **进度监控**：自动注入的 ProgressListener 会：
///
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
public class SerfileSourceFileAdapter implements SerfileSourceFilePort {

  private final DownloadClient downloadClient;
  private final ProgressListener progressListener;

  /// 构造 Serfile 数据源适配器。
  ///
  /// @param downloadClient 下载客户端
  /// @param progressListener 进度监听器（可选，为 null 则不监控进度）
  public SerfileSourceFileAdapter(
      DownloadClient downloadClient, @Nullable ProgressListener progressListener) {
    this.downloadClient = downloadClient;
    this.progressListener = progressListener;
  }

  @Override
  public Path fetch(URI remoteUrl) {
    Objects.requireNonNull(remoteUrl, "远程 URL 不能为 null");
    log.info("开始下载 Serfile XML 文件：{}", remoteUrl);

    try {
      DownloadResult result = downloadClient.downloadToTemp(remoteUrl, progressListener);

      log.info(
          "Serfile 下载完成：{}，大小：{} bytes，耗时：{}ms",
          result.filePath(),
          result.fileSize(),
          result.durationMillis());

      return result.filePath();

    } catch (DownloadException e) {
      log.error("Serfile 下载失败：{}", remoteUrl, e);
      // 将 starter 层的异常转换为 domain 层异常
      throw new SerfileDownloadException("下载 Serfile 失败：" + remoteUrl + "，原因：" + e.getMessage(), e);
    }
  }
}
