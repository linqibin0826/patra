package dev.patra.catalog.infra.fulltext.storage;

import dev.patra.catalog.domain.fulltext.exception.FullTextFileNotFoundException;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFileInfo;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import dev.patra.catalog.infra.fulltext.config.FullTextSourceProperties;
import dev.patra.tool.objectstorage.ObjectStorageOperations;
import dev.patra.tool.objectstorage.domain.DownloadResult;
import dev.patra.tool.objectstorage.domain.ObjectMetadata;
import dev.patra.tool.objectstorage.domain.ObjectNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 全文文件的 MinIO 存取实现，桶取 `patra.catalog.fulltext.bucket`（上传时懒建）。
///
/// 文件直写对象存储，引用记在 catalog 自己的全文表里（与期刊封面一致）。
///
/// @author linqibin
/// @since 0.11.0
@Component
@RequiredArgsConstructor
public class FullTextObjectStorageAdapter implements FullTextObjectStoragePort {

  private final ObjectStorageOperations objectStorage;
  private final FullTextSourceProperties properties;

  /// 写入一个本地文件。
  ///
  /// @param objectKey 对象键
  /// @param file 本地文件
  /// @param contentType 内容类型
  @Override
  public void store(String objectKey, Path file, String contentType) {
    try (InputStream in = Files.newInputStream(file)) {
      objectStorage.upload(
          properties.bucket(),
          objectKey,
          in,
          ObjectMetadata.builder()
              .contentLength(Files.size(file))
              .contentType(contentType)
              .build());
    } catch (IOException e) {
      throw new UncheckedIOException("读取待上传文件失败：" + file, e);
    }
  }

  /// 查对象元信息。
  ///
  /// @param objectKey 对象键
  /// @return 元信息
  @Override
  public Optional<StoredFileInfo> stat(String objectKey) {
    return objectStorage
        .statObject(properties.bucket(), objectKey)
        .map(o -> StoredFileInfo.of(o.getContentLength(), o.getContentType(), o.getEtag()));
  }

  /// 打开对象。
  ///
  /// @param objectKey 对象键
  /// @return 打开的文件
  @Override
  public StoredFile open(String objectKey) {
    DownloadResult result;
    try {
      result = objectStorage.download(properties.bucket(), objectKey);
    } catch (ObjectNotFoundException e) {
      throw new FullTextFileNotFoundException(objectKey, e);
    }
    return StoredFile.of(
        result.getContent(),
        StoredFileInfo.of(result.getContentLength(), result.getContentType(), result.getEtag()));
  }
}
