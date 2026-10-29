package dev.patra.catalog.app.fulltext.usecase.fetch;

import dev.patra.catalog.domain.fulltext.exception.PmcSourceException;
import dev.patra.catalog.domain.fulltext.model.read.FetchCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.ImageDimensions;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleFile;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleMetadata;
import dev.patra.catalog.domain.fulltext.model.vo.PmcVersionSelection;
import dev.patra.catalog.domain.fulltext.port.image.ImageDimensionPort;
import dev.patra.catalog.domain.fulltext.port.source.PmcArticleSourcePort;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import dev.patra.catalog.domain.fulltext.service.PmcVersionSelector;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadPort;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 抓取一篇文献的全文（不开事务，HTTP 与对象存储都在事务外）。
///
/// 列版本 → 读元数据 → 选版本 → 下载 XML、PDF、图片（逐个校验 md5、图片读宽高）写入对象存储 →
/// 交 Persister 记录。任何异常都记抓取失败；已写入对象存储的文件不清理（下次覆盖写）。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PmcFullTextFetchWorker {

  private static final Map<String, String> CONTENT_TYPES =
      Map.of(
          "xml", "application/xml",
          "pdf", "application/pdf",
          "jpg", "image/jpeg",
          "jpeg", "image/jpeg",
          "png", "image/png",
          "gif", "image/gif",
          "webp", "image/webp");

  private final PmcArticleSourcePort source;
  private final FileDownloadPort fileDownloadPort;
  private final FullTextObjectStoragePort storage;
  private final ImageDimensionPort imageDimensionPort;
  private final PmcFullTextFetchPersister persister;
  private final Clock clock;

  /// 处理一篇。
  ///
  /// @param c 候选
  /// @return 结果
  public FetchOutcome process(FetchCandidate c) {
    try {
      List<PmcArticleMetadata> versions =
          source.listVersionPrefixes(c.pmcid()).stream().map(source::getMetadata).toList();
      PmcVersionSelection selection = PmcVersionSelector.select(versions);
      if (selection.unavailableReason() != null) {
        persister.recordUnavailable(c, selection.unavailableReason());
        return FetchOutcome.UNAVAILABLE;
      }

      PmcArticleMetadata selected = selection.selected();
      String prefix = FullTextObjectStoragePort.objectPrefix(c.pmcid(), selected.versionPrefix());
      transfer(selected.versionPrefix(), prefix, selected.xml(), false);
      Long pdfSize =
          selected.pdf() != null
              ? transfer(selected.versionPrefix(), prefix, selected.pdf(), false).size()
              : null;
      List<FullTextMedia> media = new ArrayList<>();
      for (PmcArticleFile image : selected.media()) {
        Transferred t = transfer(selected.versionPrefix(), prefix, image, true);
        media.add(
            FullTextMedia.of(
                image.fileName(),
                t.dimensions().map(ImageDimensions::width).orElse(null),
                t.dimensions().map(ImageDimensions::height).orElse(null)));
      }
      persister.recordFetched(c, selected, pdfSize, media, clock.instant());
      return FetchOutcome.FETCHED;
    } catch (Exception e) {
      log.warn("全文抓取失败：publicationId={}, pmcid={}，{}", c.publicationId(), c.pmcid(), e.toString());
      persister.recordFailure(c, e);
      return FetchOutcome.FAILED;
    }
  }

  /// 下载一个文件、校验 md5、（图片）读宽高、写入对象存储，最后删临时文件。
  private Transferred transfer(
      String versionPrefix, String objectPrefix, PmcArticleFile file, boolean image)
      throws IOException {
    FileDownloadResult downloaded =
        fileDownloadPort.download(source.fileUri(versionPrefix, file.fileName()));
    Path path = downloaded.filePath();
    try {
      verifyMd5(path, file);
      Optional<ImageDimensions> dimensions =
          image ? imageDimensionPort.read(path) : Optional.empty();
      storage.store(objectPrefix + file.fileName(), path, contentType(file.fileName()));
      return new Transferred(downloaded.fileSize(), dimensions);
    } finally {
      Files.deleteIfExists(path);
    }
  }

  /// 元数据给了 md5 时校验，不符抛 `PmcSourceException`。
  private static void verifyMd5(Path path, PmcArticleFile file) throws IOException {
    if (file.md5() == null) {
      return;
    }
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 不支持 MD5", e);
    }
    try (InputStream in = new DigestInputStream(Files.newInputStream(path), digest)) {
      in.transferTo(OutputStream.nullOutputStream());
    }
    String actual = HexFormat.of().formatHex(digest.digest());
    if (!actual.equalsIgnoreCase(file.md5())) {
      throw new PmcSourceException(
          "文件 md5 不符：" + file.fileName() + "，期望 " + file.md5() + "，实际 " + actual);
    }
  }

  /// 按扩展名取内容类型。
  private static String contentType(String fileName) {
    int dot = fileName.lastIndexOf('.');
    String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
  }

  /// 一个文件的传输结果。
  private record Transferred(long size, Optional<ImageDimensions> dimensions) {}
}
