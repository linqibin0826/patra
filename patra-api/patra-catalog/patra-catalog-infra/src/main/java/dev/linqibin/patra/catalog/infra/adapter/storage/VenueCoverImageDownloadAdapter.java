package dev.linqibin.patra.catalog.infra.adapter.storage;

import com.patra.starter.objectstorage.ObjectStorageOperations;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import dev.linqibin.patra.catalog.domain.exception.FileDownloadException;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import dev.linqibin.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// LetPub 封面图下载适配器。
///
/// **实现策略**：
///
/// 1. 通过 `FileDownloadPort` 下载原图到本地临时目录
/// 2. 校验大小（上限 16 MiB）
/// 3. 以 `image/jpeg` Content-Type 上传到对象存储
/// 4. try-finally 清理临时文件（即使上传失败也必须清理）
///
/// **异常转译**：
///
/// - 响应为空 / 上传失败 → `FileDownloadException(DEP_UNAVAILABLE)`
/// - 大小超限 → `FileDownloadException(RULE_VIOLATION)`
/// - IOException → `FileDownloadException(DEP_UNAVAILABLE)` 包装
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueCoverImageDownloadAdapter implements VenueCoverImageDownloadPort {

  private static final long MAX_COVER_BYTES = 16L * 1024 * 1024;
  private static final String COVER_CONTENT_TYPE = "image/jpeg";

  private final FileDownloadPort fileDownloadPort;
  private final ObjectStorageOperations objectStorage;
  private final VenueCoverImageProperties properties;

  @Override
  public String downloadAndStore(URI sourceUrl, String targetObjectKey) {
    FileDownloadResult downloadResult = null;
    try {
      downloadResult = fileDownloadPort.download(sourceUrl);
      if (downloadResult.fileSize() <= 0) {
        throw new FileDownloadException("封面响应为空: " + sourceUrl, StandardErrorTrait.DEP_UNAVAILABLE);
      }
      if (downloadResult.fileSize() > MAX_COVER_BYTES) {
        throw new FileDownloadException(
            "封面大小超限: " + downloadResult.fileSize() + " bytes (limit=" + MAX_COVER_BYTES + ")",
            StandardErrorTrait.RULE_VIOLATION);
      }
      try (InputStream stream = Files.newInputStream(downloadResult.filePath())) {
        ObjectMetadata metadata =
            ObjectMetadata.builder()
                .contentType(COVER_CONTENT_TYPE)
                .contentLength(downloadResult.fileSize())
                .build();
        objectStorage.upload(properties.venueCover(), targetObjectKey, stream, metadata);
      }
      log.info(
          "封面上传成功: venueKey={} size={} source={}",
          targetObjectKey,
          downloadResult.fileSize(),
          sourceUrl);
      return targetObjectKey;
    } catch (FileDownloadException e) {
      // 透传：FileDownloadException 已携带正确的 trait，避免被下方 RuntimeException 分支二次包装。
      throw e;
    } catch (IOException e) {
      throw new FileDownloadException(
          "读取临时封面文件失败: " + (downloadResult == null ? "null" : downloadResult.filePath()),
          e,
          StandardErrorTrait.DEP_UNAVAILABLE);
    } catch (RuntimeException e) {
      throw new FileDownloadException(
          "上传封面到对象存储失败: " + targetObjectKey, e, StandardErrorTrait.DEP_UNAVAILABLE);
    } finally {
      if (downloadResult != null) {
        try {
          Files.deleteIfExists(downloadResult.filePath());
        } catch (IOException cleanup) {
          log.warn("删除临时封面文件失败: {}", downloadResult.filePath(), cleanup);
        }
      }
    }
  }
}
