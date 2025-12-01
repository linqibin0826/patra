package com.patra.starter.restclient.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/// 默认下载客户端实现。
///
/// 使用 RestClient 流式下载文件，支持进度监控。
///
/// **核心特性**：
/// - 流式读取：使用 64KB 缓冲区，内存友好
/// - 进度节流：每 500ms 更新一次进度，避免高频回调
/// - 速度计算：基于滑动窗口计算瞬时速度
/// - 错误处理：区分超时、IO 错误、HTTP 错误
///
/// **技术选型**：使用 RestClient 而非 WebClient，因为：
/// - 同步下载更简单（大文件一次性下载）
/// - 与项目现有技术栈一致
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class DefaultDownloadClient implements DownloadClient {

  /// 缓冲区大小（64KB）
  private static final int BUFFER_SIZE = 65536;

  /// 进度更新间隔（500ms）
  private static final int PROGRESS_UPDATE_INTERVAL_MS = 500;

  /// 临时文件前缀
  private static final String TEMP_FILE_PREFIX = "download-";

  /// 临时文件后缀
  private static final String TEMP_FILE_SUFFIX = ".tmp";

  private final RestClient restClient;

  /// 创建下载客户端。
  ///
  /// @param restClient RestClient 实例（建议使用 longRunningRestClient）
  public DefaultDownloadClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public DownloadResult download(URI url, Path targetPath, @Nullable ProgressListener listener) {
    log.debug("开始下载文件: {} -> {}", url, targetPath);

    try {
      return executeDownload(url, targetPath, listener);
    } catch (DownloadException e) {
      notifyError(listener, e, null);
      throw e;
    } catch (ResourceAccessException e) {
      var ex = DownloadException.timeout(e);
      notifyError(listener, ex, null);
      throw ex;
    } catch (Exception e) {
      var ex = DownloadException.ioError(e);
      notifyError(listener, ex, null);
      throw ex;
    }
  }

  @Override
  public DownloadResult downloadToTemp(URI url, @Nullable ProgressListener listener) {
    try {
      Path tempFile =
          Files.createTempFile(
              TEMP_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 8) + "-",
              TEMP_FILE_SUFFIX);
      return download(url, tempFile, listener);
    } catch (IOException e) {
      throw DownloadException.ioError(e);
    }
  }

  /// 执行下载核心逻辑。
  private DownloadResult executeDownload(
      URI url, Path targetPath, @Nullable ProgressListener listener) {
    return restClient
        .get()
        .uri(url)
        .exchange(
            (request, response) -> {
              // 检查 HTTP 状态码
              if (response.getStatusCode().isError()) {
                throw DownloadException.httpError(response.getStatusCode().value());
              }

              long totalBytes = response.getHeaders().getContentLength();
              long startTime = System.currentTimeMillis();

              // 用于速度计算的滑动窗口
              long[] lastUpdateState = {startTime, 0L}; // [lastUpdateTime, lastBytes]
              DownloadProgress[] lastProgress = {null};

              try (InputStream is = response.getBody();
                  OutputStream os = Files.newOutputStream(targetPath)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long bytesDownloaded = 0;

                while ((bytesRead = is.read(buffer)) != -1) {
                  os.write(buffer, 0, bytesRead);
                  bytesDownloaded += bytesRead;

                  // 节流：只在达到更新间隔时计算和通知进度
                  long now = System.currentTimeMillis();
                  if (listener != null && now - lastUpdateState[0] >= PROGRESS_UPDATE_INTERVAL_MS) {
                    DownloadProgress progress =
                        calculateProgress(
                            bytesDownloaded,
                            totalBytes,
                            startTime,
                            now,
                            lastUpdateState[1],
                            lastUpdateState[0]);

                    listener.onProgress(progress);
                    lastProgress[0] = progress;

                    // 更新滑动窗口
                    lastUpdateState[0] = now;
                    lastUpdateState[1] = bytesDownloaded;
                  }
                }

                // 计算最终进度
                long endTime = System.currentTimeMillis();
                DownloadProgress finalProgress =
                    calculateProgress(
                        bytesDownloaded,
                        totalBytes,
                        startTime,
                        endTime,
                        lastUpdateState[1],
                        lastUpdateState[0]);

                // 通知完成
                if (listener != null) {
                  listener.onComplete(finalProgress);
                }

                log.debug(
                    "文件下载完成: {}, 大小: {} bytes, 耗时: {}ms",
                    targetPath,
                    bytesDownloaded,
                    endTime - startTime);

                return new DownloadResult(targetPath, bytesDownloaded, finalProgress);

              } catch (IOException e) {
                // 清理可能已部分写入的文件
                cleanupFile(targetPath);
                throw DownloadException.ioError(e);
              }
            });
  }

  /// 计算下载进度。
  ///
  /// @param bytesDownloaded 已下载字节数
  /// @param totalBytes 总字节数（-1 表示未知）
  /// @param startTime 开始时间
  /// @param now 当前时间
  /// @param lastBytes 上次更新时的字节数（用于计算瞬时速度）
  /// @param lastTime 上次更新时间
  /// @return 进度信息
  private DownloadProgress calculateProgress(
      long bytesDownloaded,
      long totalBytes,
      long startTime,
      long now,
      long lastBytes,
      long lastTime) {

    long elapsed = now - startTime;
    int percentage = totalBytes > 0 ? (int) ((bytesDownloaded * 100) / totalBytes) : -1;

    // 计算瞬时速度（基于滑动窗口）
    long timeDelta = now - lastTime;
    long bytesDelta = bytesDownloaded - lastBytes;
    long speed = timeDelta > 0 ? (bytesDelta * 1000) / timeDelta : 0;

    // 预估剩余时间
    long remaining = -1;
    if (speed > 0 && totalBytes > 0) {
      remaining = (totalBytes - bytesDownloaded) / speed;
    }

    return new DownloadProgress(bytesDownloaded, totalBytes, percentage, speed, remaining, elapsed);
  }

  /// 通知监听器发生错误。
  private void notifyError(
      @Nullable ProgressListener listener,
      DownloadException exception,
      @Nullable DownloadProgress lastProgress) {
    if (listener != null) {
      try {
        listener.onError(exception, lastProgress);
      } catch (Exception e) {
        log.warn("通知监听器错误时发生异常: {}", e.getMessage());
      }
    }
  }

  /// 清理文件（下载失败时）。
  private void cleanupFile(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("清理临时文件失败: {}, 原因: {}", file, e.getMessage());
    }
  }
}
