package com.patra.catalog.infra.adapter.venue;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.VenueSourceFilePort;
import com.patra.catalog.infra.config.OpenAlexCacheProperties;
import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.core.async.AsyncExecutorRegistry;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/// OpenAlex Venue 数据源文件适配器。
///
/// 实现缓存优先策略：
///
/// 1. 检查 MinIO 缓存中是否存在文件
/// 2. 存在则从 MinIO 下载到本地临时目录
/// 3. 不存在则从 AWS S3 公开存储桶下载，并异步上传到 MinIO
///
/// **静默降级**：缓存检查/下载失败时记录 warn 日志，自动回退到远程下载。
///
/// **与 MeshSourceFileAdapter 的差异**：
///
/// - Venue 需要先获取 manifest（JSON 格式），再下载多个分区文件
/// - Venue 使用 AWS S3 公开存储桶（无需认证）
/// - Venue 分区文件使用 `updated_date=YYYY-MM-DD/part_XXX.gz` 格式
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class VenueSourceFileAdapter implements VenueSourceFilePort {

  private static final String TEMP_FILE_PREFIX = "openalex-venue-";
  private static final String CACHE_UPLOAD_EXECUTOR = "cache-upload";
  private static final AtomicBoolean cacheUploadDisabledLogged = new AtomicBoolean(false);

  private final FileDownloadPort fileDownloadPort;
  private final ObjectStorageOperations objectStorage;
  private final OpenAlexCacheProperties cacheProperties;
  private final AsyncExecutorRegistry asyncExecutorRegistry;

  /// 构造 Venue 数据源文件适配器。
  ///
  /// @param fileDownloadPort 文件下载端口（用于从远程 URL 下载）
  /// @param objectStorage 对象存储操作（用于缓存读写）
  /// @param cacheProperties 缓存配置属性
  /// @param asyncExecutorRegistry 异步执行器注册表（用于缓存上传）
  public VenueSourceFileAdapter(
      FileDownloadPort fileDownloadPort,
      ObjectStorageOperations objectStorage,
      OpenAlexCacheProperties cacheProperties,
      AsyncExecutorRegistry asyncExecutorRegistry) {
    this.fileDownloadPort = fileDownloadPort;
    this.objectStorage = objectStorage;
    this.cacheProperties = cacheProperties;
    this.asyncExecutorRegistry = asyncExecutorRegistry;
  }

  @Override
  public OpenAlexManifest fetchManifest() {
    log.info("获取 OpenAlex Sources manifest");

    Path manifestFile;

    // 缓存未启用时直接从远程下载
    if (!cacheProperties.enabled()) {
      log.info("OpenAlex 缓存未启用，直接从远程下载 manifest: {}", cacheProperties.getManifestUrl());
      manifestFile = fileDownloadPort.downloadToTemp(URI.create(cacheProperties.getManifestUrl()));
    } else {
      manifestFile =
          fetchFromCacheOrRemote(
              cacheProperties.getManifestCacheKey(), cacheProperties.getManifestUrl(), "manifest");
    }

    try {
      return OpenAlexManifestParser.parseManifest(manifestFile);
    } finally {
      OpenAlexManifestParser.cleanupTempFile(manifestFile);
    }
  }

  @Override
  public Path fetchPartitionFile(String relativePath) {
    log.debug("获取 OpenAlex 分区文件: {}", relativePath);

    // 缓存未启用时直接从远程下载
    if (!cacheProperties.enabled()) {
      return fileDownloadPort.downloadToTemp(
          URI.create(cacheProperties.getPartitionUrl(relativePath)));
    }

    return fetchFromCacheOrRemote(
        cacheProperties.getCacheKey(relativePath),
        cacheProperties.getPartitionUrl(relativePath),
        relativePath);
  }

  @Override
  public List<Path> fetchAllPartitionFiles(OpenAlexManifest manifest) {
    log.info("开始获取所有分区文件，共 {} 个", manifest.entries().size());

    List<Path> localFiles = new ArrayList<>();
    int total = manifest.entries().size();
    int current = 0;

    for (String relativePath : manifest.getRelativePaths()) {
      current++;
      if (current % 10 == 0 || current == total) {
        log.info("下载进度: {}/{}", current, total);
      }

      Path localFile = fetchPartitionFile(relativePath);
      localFiles.add(localFile);
    }

    log.info("所有分区文件获取完成，共 {} 个", localFiles.size());
    return localFiles;
  }

  /// 从缓存或远程获取文件。
  private Path fetchFromCacheOrRemote(String cacheKey, String remoteUrl, String displayName) {
    // 1. 尝试从缓存获取
    try {
      if (objectStorage.exists(cacheProperties.bucket(), cacheKey)) {
        log.debug("从缓存获取文件: bucket={}, key={}", cacheProperties.bucket(), cacheKey);
        return downloadFromCache(cacheKey, displayName);
      }
      log.debug("缓存中不存在文件，将从远程下载: {}", remoteUrl);
    } catch (Exception e) {
      log.warn("检查缓存失败，降级到远程下载: {}", e.getMessage());
    }

    // 2. 从远程下载
    Path tempFile = fileDownloadPort.downloadToTemp(URI.create(remoteUrl));

    // 3. 异步上传到缓存
    uploadToCacheAsync(cacheKey, tempFile, displayName);

    return tempFile;
  }

  /// 从缓存下载文件到本地临时目录。
  private Path downloadFromCache(String cacheKey, String displayName) {
    try {
      Path tempDir = Files.createTempDirectory(TEMP_FILE_PREFIX);
      Path tempFile = tempDir.resolve("cached-" + System.nanoTime() + ".gz");
      objectStorage.downloadToFile(cacheProperties.bucket(), cacheKey, tempFile);
      log.debug("从缓存下载文件完成: {}", tempFile);
      return tempFile;
    } catch (IOException e) {
      throw new FileDownloadException(
          "创建临时目录失败: " + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    } catch (Exception e) {
      throw new FileDownloadException(
          "从缓存下载文件失败 [" + displayName + "]: " + e.getMessage(),
          e,
          StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  /// 异步上传文件到缓存。
  private void uploadToCacheAsync(String cacheKey, Path localFile, String displayName) {
    if (!asyncExecutorRegistry.hasExecutor(CACHE_UPLOAD_EXECUTOR)) {
      if (cacheUploadDisabledLogged.compareAndSet(false, true)) {
        log.info(
            "异步线程池 '{}' 未配置，缓存上传功能已禁用。" + "如需启用，请在配置文件中添加 patra.async.pools.{} 配置",
            CACHE_UPLOAD_EXECUTOR,
            CACHE_UPLOAD_EXECUTOR);
      }
      return;
    }

    CompletableFuture.runAsync(
        () -> {
          try {
            long fileSize = Files.size(localFile);
            try (InputStream is = Files.newInputStream(localFile)) {
              ObjectMetadata metadata =
                  ObjectMetadata.builder()
                      .contentLength(fileSize)
                      .contentType("application/gzip")
                      .build();
              objectStorage.upload(cacheProperties.bucket(), cacheKey, is, metadata);
              log.debug(
                  "文件已上传到缓存: bucket={}, key={}, size={} bytes",
                  cacheProperties.bucket(),
                  cacheKey,
                  fileSize);
            }
          } catch (Exception e) {
            log.warn("上传文件到缓存失败（不影响导入流程）[{}]: {}", displayName, e.getMessage());
          }
        },
        asyncExecutorRegistry.getExecutor(CACHE_UPLOAD_EXECUTOR));
  }
}
