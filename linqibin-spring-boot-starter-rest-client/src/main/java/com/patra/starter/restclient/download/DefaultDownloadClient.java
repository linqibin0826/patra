package com.patra.starter.restclient.download;

import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.restclient.config.DownloadProperties;
import com.patra.starter.restclient.download.strategy.StreamingDownloader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/// 默认下载客户端实现。
///
/// 统一提供流式下载和落盘下载能力，并支持写入策略、FTP 账号与重试配置。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class DefaultDownloadClient implements DownloadClient {

  /// 进度更新间隔（500ms）。
  private static final int PROGRESS_UPDATE_INTERVAL_MS = 500;

  /// 临时文件前缀。
  private static final String TEMP_FILE_PREFIX = "download-";

  /// 临时文件后缀。
  private static final String TEMP_FILE_SUFFIX = ".tmp";

  private final List<StreamingDownloader> streamingDownloaders;
  private final DownloadProperties properties;

  /// 创建下载客户端。
  ///
  /// @param streamingDownloaders 流式下载策略列表
  /// @param properties 下载配置
  public DefaultDownloadClient(
      List<StreamingDownloader> streamingDownloaders, DownloadProperties properties) {
    this.streamingDownloaders =
        Objects.requireNonNull(streamingDownloaders, "streamingDownloaders 不能为 null");
    this.properties = Objects.requireNonNull(properties, "properties 不能为 null");
  }

  @Override
  public StreamingDownloadResponse openStream(URI url, @Nullable DownloadOptions options) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");

    if (url.getScheme() == null) {
      throw DownloadException.invalidUrl(String.valueOf(url));
    }

    DownloadOptions resolvedOptions = resolveOptions(options);

    StreamingDownloader downloader =
        streamingDownloaders.stream()
            .filter(strategy -> strategy.supports(url))
            .findFirst()
            .orElseThrow(() -> DownloadException.unsupportedScheme(url.getScheme()));

    return downloader.openStream(url, resolvedOptions);
  }

  @Override
  public DownloadResult download(DownloadRequest request) {
    Objects.requireNonNull(request, "下载请求不能为 null");
    Objects.requireNonNull(request.url(), "下载 URL 不能为 null");

    DownloadOptions resolvedOptions = resolveOptions(request.options());
    Path targetPath = resolveTargetPath(request.url(), request.targetPath(), resolvedOptions);

    if (Files.exists(targetPath) && !Files.isRegularFile(targetPath)) {
      throw DownloadException.targetNotRegularFile(targetPath.toString());
    }
    if (resolvedOptions.writeStrategy() == WriteStrategy.SKIP && Files.exists(targetPath)) {
      DownloadResult skippedResult = buildSkippedResult(targetPath);
      if (resolvedOptions.progressListener() != null) {
        resolvedOptions.progressListener().onComplete(skippedResult.finalProgress());
      }
      return skippedResult;
    }
    if (resolvedOptions.writeStrategy() == WriteStrategy.FAIL && Files.exists(targetPath)) {
      throw DownloadException.fileAlreadyExists(targetPath.toString());
    }

    if (resolvedOptions.createDirs()) {
      createParentDirectories(targetPath);
    }

    log.debug("开始下载文件: {} -> {}", request.url(), targetPath);

    try (StreamingDownloadResponse streamingResult = openStream(request.url(), resolvedOptions)) {
      return writeStreamToFile(streamingResult, targetPath, resolvedOptions);
    } catch (DownloadException e) {
      notifyError(resolvedOptions.progressListener(), e, null);
      if (resolvedOptions.cleanupOnFailure()) {
        cleanupFile(targetPath);
      }
      throw e;
    } catch (Exception e) {
      DownloadException ex =
          new DownloadException("下载失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
      notifyError(resolvedOptions.progressListener(), ex, null);
      if (resolvedOptions.cleanupOnFailure()) {
        cleanupFile(targetPath);
      }
      throw ex;
    }
  }

  @Override
  public DownloadResult downloadToTemp(URI url, @Nullable DownloadOptions options) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");

    Path tempDir = properties.getTempDir();
    if (tempDir == null) {
      tempDir = Path.of(System.getProperty("java.io.tmpdir"));
    }
    Path targetPath =
        tempDir.resolve(
            TEMP_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 8) + TEMP_FILE_SUFFIX);
    return download(new DownloadRequest(url, targetPath, options));
  }

  private DownloadResult writeStreamToFile(
      StreamingDownloadResponse streamingResult, Path targetPath, DownloadOptions options) {
    long totalBytes = streamingResult.contentLength();
    long startTime = System.currentTimeMillis();
    long[] lastUpdateState = {startTime, 0L};
    DownloadProgress[] lastProgress = {null};

    try (InputStream is = streamingResult.inputStream();
        OutputStream os =
            Files.newOutputStream(
                targetPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {

      byte[] buffer = new byte[options.bufferSize()];
      int bytesRead;
      long bytesDownloaded = 0;

      while ((bytesRead = is.read(buffer)) != -1) {
        os.write(buffer, 0, bytesRead);
        bytesDownloaded += bytesRead;

        long now = System.currentTimeMillis();
        if (options.progressListener() != null
            && now - lastUpdateState[0] >= PROGRESS_UPDATE_INTERVAL_MS) {
          DownloadProgress progress =
              calculateProgress(
                  bytesDownloaded,
                  totalBytes,
                  startTime,
                  now,
                  lastUpdateState[1],
                  lastUpdateState[0]);
          options.progressListener().onProgress(progress);
          lastProgress[0] = progress;
          lastUpdateState[0] = now;
          lastUpdateState[1] = bytesDownloaded;
        }
      }

      long endTime = System.currentTimeMillis();
      DownloadProgress finalProgress =
          calculateProgress(
              bytesDownloaded,
              totalBytes,
              startTime,
              endTime,
              lastUpdateState[1],
              lastUpdateState[0]);

      if (options.progressListener() != null) {
        options.progressListener().onComplete(finalProgress);
      }

      log.debug(
          "文件下载完成: {}, 大小: {} bytes, 耗时: {}ms", targetPath, bytesDownloaded, endTime - startTime);

      return new DownloadResult(targetPath, bytesDownloaded, finalProgress);

    } catch (IOException e) {
      if (options.cleanupOnFailure()) {
        cleanupFile(targetPath);
      }
      throw DownloadException.ioError(e);
    }
  }

  private DownloadOptions resolveOptions(@Nullable DownloadOptions options) {
    WriteStrategy writeStrategy =
        options != null && options.writeStrategy() != null
            ? options.writeStrategy()
            : properties.getWriteStrategy();
    boolean createDirs =
        options != null && options.createDirs() != null
            ? options.createDirs()
            : properties.isCreateDirs();
    boolean cleanupOnFailure =
        options != null && options.cleanupOnFailure() != null
            ? options.cleanupOnFailure()
            : properties.isCleanupOnFailure();
    int bufferSize =
        options != null && options.bufferSize() != null
            ? options.bufferSize()
            : properties.getBufferSize();
    if (bufferSize <= 0) {
      throw new DownloadException("bufferSize 必须大于 0", StandardErrorTrait.RULE_VIOLATION);
    }
    FtpCredentials ftpCredentials = options != null ? options.ftpCredentials() : null;
    ProgressListener progressListener = options != null ? options.progressListener() : null;

    return new DownloadOptions(
        writeStrategy, createDirs, cleanupOnFailure, bufferSize, ftpCredentials, progressListener);
  }

  private Path resolveTargetPath(URI url, @Nullable Path targetPath, DownloadOptions options) {
    if (targetPath != null) {
      return targetPath;
    }
    Path baseDir = properties.getBaseDir();
    if (baseDir == null) {
      throw DownloadException.targetPathMissing();
    }
    String fileName = extractFileName(url);
    return baseDir.resolve(fileName);
  }

  private String extractFileName(URI url) {
    String path = url.getPath();
    if (path == null || path.isBlank() || "/".equals(path)) {
      return TEMP_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 8) + TEMP_FILE_SUFFIX;
    }
    int lastSlash = path.lastIndexOf('/');
    String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    if (fileName.isBlank()) {
      return TEMP_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 8) + TEMP_FILE_SUFFIX;
    }
    return fileName;
  }

  private void createParentDirectories(Path targetPath) {
    Path parent = targetPath.getParent();
    if (parent != null && !Files.exists(parent)) {
      try {
        Files.createDirectories(parent);
      } catch (IOException e) {
        throw DownloadException.ioError(e);
      }
    }
  }

  private DownloadResult buildSkippedResult(Path targetPath) {
    try {
      long fileSize = Files.size(targetPath);
      DownloadProgress progress = new DownloadProgress(fileSize, fileSize, 100, 0, 0, 0);
      return new DownloadResult(targetPath, fileSize, progress);
    } catch (IOException e) {
      throw DownloadException.ioError(e);
    }
  }

  private DownloadProgress calculateProgress(
      long bytesDownloaded,
      long totalBytes,
      long startTime,
      long now,
      long lastBytes,
      long lastTime) {

    long elapsed = now - startTime;
    int percentage = totalBytes > 0 ? (int) ((bytesDownloaded * 100) / totalBytes) : -1;

    long timeDelta = now - lastTime;
    long bytesDelta = bytesDownloaded - lastBytes;
    long speed = timeDelta > 0 ? (bytesDelta * 1000) / timeDelta : 0;

    long remaining = -1;
    if (speed > 0 && totalBytes > 0) {
      remaining = (totalBytes - bytesDownloaded) / speed;
    }

    return new DownloadProgress(bytesDownloaded, totalBytes, percentage, speed, remaining, elapsed);
  }

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

  private void cleanupFile(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("清理临时文件失败: {}, 原因: {}", file, e.getMessage());
    }
  }
}
