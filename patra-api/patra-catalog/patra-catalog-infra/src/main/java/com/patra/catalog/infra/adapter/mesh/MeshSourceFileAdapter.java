package com.patra.catalog.infra.adapter.mesh;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.model.enums.MeshDataType;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.MeshSourceFilePort;
import com.patra.catalog.infra.config.MeshCacheProperties;
import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.core.async.AsyncExecutorRegistry;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

/// MeSH 数据源文件适配器。
///
/// 实现缓存优先策略：
///
/// 1. 检查对象存储中是否存在缓存文件
/// 2. 存在则从对象存储下载到本地临时目录
/// 3. 不存在则从远程 URL 下载，并异步上传到对象存储
///
/// **静默降级**：缓存检查/下载失败时记录 warn 日志，自动回退到远程下载。
///
/// **键格式**：{keyPrefix}/descriptors/desc{version}.xml
///
/// @author linqibin
/// @since 0.1.0
/// @see com.patra.catalog.infra.config.MeshSourceFileConfiguration
@Slf4j
public class MeshSourceFileAdapter implements MeshSourceFilePort {

  /// 缓存键模板：{keyPrefix}/{dataTypeCode}s/{filePrefix}{version}.xml
  ///
  /// 示例：mesh/descriptors/desc2025.xml, mesh/qualifiers/qual2025.xml
  private static final String CACHE_KEY_TEMPLATE = "%s/%ss/%s%s.xml";
  private static final String TEMP_FILE_PREFIX = "mesh-";

  /// 异步线程池名称（用于缓存上传）。
  private static final String CACHE_UPLOAD_EXECUTOR = "cache-upload";

  private final FileDownloadPort fileDownloadPort;
  private final ObjectStorageOperations objectStorage;
  private final MeshCacheProperties cacheProperties;
  private final AsyncExecutorRegistry asyncExecutorRegistry;

  /// 构造 MeSH 数据源文件适配器。
  ///
  /// @param fileDownloadPort 文件下载端口（用于从远程 URL 下载）
  /// @param objectStorage 对象存储操作（用于缓存读写）
  /// @param cacheProperties 缓存配置属性
  /// @param asyncExecutorRegistry 异步执行器注册表（用于缓存上传）
  public MeshSourceFileAdapter(
      FileDownloadPort fileDownloadPort,
      ObjectStorageOperations objectStorage,
      MeshCacheProperties cacheProperties,
      AsyncExecutorRegistry asyncExecutorRegistry) {
    this.fileDownloadPort = fileDownloadPort;
    this.objectStorage = objectStorage;
    this.cacheProperties = cacheProperties;
    this.asyncExecutorRegistry = asyncExecutorRegistry;
  }

  @Override
  public Path fetchDescriptorFile(String meshVersion, URI remoteUrl) {
    return fetchFile(
        meshVersion,
        remoteUrl,
        buildCacheKey(MeshDataType.DESCRIPTOR, meshVersion),
        MeshDataType.DESCRIPTOR);
  }

  @Override
  public Path fetchQualifierFile(String meshVersion, URI remoteUrl) {
    return fetchFile(
        meshVersion,
        remoteUrl,
        buildCacheKey(MeshDataType.QUALIFIER, meshVersion),
        MeshDataType.QUALIFIER);
  }

  /// 获取 MeSH 文件的核心逻辑。
  ///
  /// @param meshVersion MeSH 版本号
  /// @param remoteUrl 远程文件 URL
  /// @param cacheKey 缓存键
  /// @param dataType MeSH 数据类型
  /// @return 本地临时文件路径
  private Path fetchFile(
      String meshVersion, URI remoteUrl, String cacheKey, MeshDataType dataType) {
    String displayName = dataType.getDisplayName();

    // 缓存未启用时直接从远程下载
    if (!cacheProperties.enabled()) {
      log.info("MeSH 缓存未启用，直接从远程下载{}文件: {}", displayName, remoteUrl);
      return fileDownloadPort.downloadToTemp(remoteUrl);
    }

    // 1. 尝试从缓存获取
    try {
      if (objectStorage.exists(cacheProperties.bucket(), cacheKey)) {
        log.info(
            "从对象存储缓存获取 MeSH {}文件: bucket={}, key={}",
            displayName,
            cacheProperties.bucket(),
            cacheKey);
        return downloadFromCache(cacheKey, dataType);
      }
      log.info("对象存储缓存中不存在 MeSH {}文件，将从远程下载: {}", displayName, remoteUrl);
    } catch (Exception e) {
      log.warn("检查 MeSH {}缓存失败，降级到远程下载: {}", displayName, e.getMessage());
    }

    // 2. 从远程下载
    Path tempFile = fileDownloadPort.downloadToTemp(remoteUrl);

    // 3. 异步上传到缓存（不阻塞主流程）
    uploadToCacheAsync(cacheKey, tempFile, dataType);

    return tempFile;
  }

  /// 从缓存下载文件到本地临时目录。
  ///
  /// @param cacheKey 缓存键
  /// @param dataType MeSH 数据类型
  /// @return 本地临时文件路径
  private Path downloadFromCache(String cacheKey, MeshDataType dataType) {
    String displayName = dataType.getDisplayName();
    try {
      // 创建临时文件路径（不实际创建文件，因为 MinIO downloadObject 需要目标文件不存在）
      Path tempDir = Files.createTempDirectory(TEMP_FILE_PREFIX);
      Path tempFile = tempDir.resolve(dataType.getCode() + "-" + System.nanoTime() + ".xml");
      objectStorage.downloadToFile(cacheProperties.bucket(), cacheKey, tempFile);
      log.info("从缓存下载 MeSH {}文件完成: {}", displayName, tempFile);
      return tempFile;
    } catch (IOException e) {
      throw new FileDownloadException(
          "创建临时目录失败: " + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    } catch (Exception e) {
      throw new FileDownloadException(
          "从缓存下载 MeSH " + displayName + "文件失败: " + e.getMessage(),
          e,
          StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  /// 异步上传文件到缓存。
  ///
  /// 使用命名线程池 `cache-upload` 执行异步上传任务，
  /// 上传失败时仅记录警告日志，不影响主流程。
  ///
  /// **线程池配置**：
  ///
  /// ```yaml
  /// patra:
  ///   async:
  ///     pools:
  ///       cache-upload:
  ///         core-size: 2
  ///         max-size: 4
  ///         queue-capacity: 50
  /// ```
  ///
  /// @param cacheKey 缓存键
  /// @param localFile 本地文件路径
  /// @param dataType MeSH 数据类型
  private void uploadToCacheAsync(String cacheKey, Path localFile, MeshDataType dataType) {
    String displayName = dataType.getDisplayName();

    // 检查线程池是否配置
    if (!asyncExecutorRegistry.hasExecutor(CACHE_UPLOAD_EXECUTOR)) {
      log.warn(
          "异步线程池 '{}' 未配置，跳过缓存上传。请在配置文件中添加 patra.async.pools.{} 配置",
          CACHE_UPLOAD_EXECUTOR,
          CACHE_UPLOAD_EXECUTOR);
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
                      .contentType("application/xml")
                      .build();
              objectStorage.upload(cacheProperties.bucket(), cacheKey, is, metadata);
              log.info(
                  "MeSH {}文件已上传到缓存: bucket={}, key={}, size={} bytes",
                  displayName,
                  cacheProperties.bucket(),
                  cacheKey,
                  fileSize);
            }
          } catch (Exception e) {
            log.warn("上传 MeSH {}文件到缓存失败（不影响导入流程）: {}", displayName, e.getMessage());
          }
        },
        asyncExecutorRegistry.getExecutor(CACHE_UPLOAD_EXECUTOR));
  }

  /// 构建缓存键。
  ///
  /// 格式：{keyPrefix}/{dataTypeCode}s/{filePrefix}{version}.xml
  ///
  /// 示例：
  /// - DESCRIPTOR + 2025 → mesh/descriptors/desc2025.xml
  /// - QUALIFIER + 2025 → mesh/qualifiers/qual2025.xml
  ///
  /// @param dataType MeSH 数据类型
  /// @param meshVersion MeSH 版本号
  /// @return 缓存键
  private String buildCacheKey(MeshDataType dataType, String meshVersion) {
    String filePrefix = dataType.getCode().substring(0, 4); // desc, qual
    return String.format(
        CACHE_KEY_TEMPLATE,
        cacheProperties.keyPrefix(),
        dataType.getCode(),
        filePrefix,
        meshVersion);
  }
}
