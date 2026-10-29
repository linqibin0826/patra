package dev.patra.catalog.app.fulltext.usecase.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.fulltext.exception.PmcSourceException;
import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.read.FetchCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.ImageDimensions;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleFile;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleMetadata;
import dev.patra.catalog.domain.fulltext.port.image.ImageDimensionPort;
import dev.patra.catalog.domain.fulltext.port.source.PmcArticleSourcePort;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadPort;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/// `PmcFullTextFetchWorker` 单元测试。
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PmcFullTextFetchWorker")
class PmcFullTextFetchWorkerTest {

  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");
  private static final FetchCandidate CANDIDATE =
      FetchCandidate.builder().publicationId(7L).pmcid("PMC1").build();

  @Mock private PmcArticleSourcePort source;
  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private FullTextObjectStoragePort storage;
  @Mock private ImageDimensionPort imageDimensionPort;
  @Mock private PmcFullTextFetchPersister persister;
  @Captor private ArgumentCaptor<List<FullTextMedia>> mediaCaptor;
  @TempDir Path tempDir;

  private PmcFullTextFetchWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new PmcFullTextFetchWorker(
            source,
            fileDownloadPort,
            storage,
            imageDimensionPort,
            persister,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(source.fileUri(anyString(), anyString()))
        .thenAnswer(
            inv -> URI.create("https://pmc/" + inv.getArgument(0) + "/" + inv.getArgument(1)));
  }

  @Test
  @DisplayName("下载 XML、PDF 与图片写入存储并记 FETCHED，图片带宽高")
  void should_record_fetched_and_store_all_files() throws Exception {
    PmcArticleMetadata metadata =
        metadata(
            file("PMC1.2.xml", "<article/>"),
            file("PMC1.2.pdf", "%PDF"),
            List.of(file("g1.webp", "img1"), file("g2.jpg", "img2")));
    when(source.listVersionPrefixes("PMC1")).thenReturn(List.of("PMC1.2"));
    when(source.getMetadata("PMC1.2")).thenReturn(metadata);
    stubDownload("PMC1.2.xml", "<article/>");
    stubDownload("PMC1.2.pdf", "%PDF");
    stubDownload("g1.webp", "img1");
    stubDownload("g2.jpg", "img2");
    when(imageDimensionPort.read(any()))
        .thenReturn(Optional.of(ImageDimensions.of(1600, 2159)))
        .thenReturn(Optional.empty());

    assertThat(worker.process(CANDIDATE)).isEqualTo(FetchOutcome.FETCHED);

    verify(storage).store(eq("pmc/PMC1/PMC1.2/PMC1.2.xml"), any(), eq("application/xml"));
    verify(storage).store(eq("pmc/PMC1/PMC1.2/PMC1.2.pdf"), any(), eq("application/pdf"));
    verify(storage).store(eq("pmc/PMC1/PMC1.2/g1.webp"), any(), eq("image/webp"));
    verify(storage).store(eq("pmc/PMC1/PMC1.2/g2.jpg"), any(), eq("image/jpeg"));
    verify(persister)
        .recordFetched(eq(CANDIDATE), eq(metadata), eq(4L), mediaCaptor.capture(), eq(NOW));
    assertThat(mediaCaptor.getValue())
        .containsExactly(
            FullTextMedia.of("g1.webp", 1600, 2159), FullTextMedia.of("g2.jpg", null, null));
  }

  @Test
  @DisplayName("桶里没有这篇时记 NOT_IN_BUCKET")
  void should_record_unavailable_not_in_bucket() {
    when(source.listVersionPrefixes("PMC1")).thenReturn(List.of());

    assertThat(worker.process(CANDIDATE)).isEqualTo(FetchOutcome.UNAVAILABLE);

    verify(persister).recordUnavailable(CANDIDATE, FullTextUnavailableReason.NOT_IN_BUCKET);
  }

  @Test
  @DisplayName("md5 不符时记抓取失败，不写记录为 FETCHED")
  void should_record_failure_with_backoff_when_md5_mismatch() throws Exception {
    PmcArticleMetadata metadata =
        metadata(PmcArticleFile.of("PMC1.2.xml", "0".repeat(32)), null, List.of());
    when(source.listVersionPrefixes("PMC1")).thenReturn(List.of("PMC1.2"));
    when(source.getMetadata("PMC1.2")).thenReturn(metadata);
    stubDownload("PMC1.2.xml", "<article/>");

    assertThat(worker.process(CANDIDATE)).isEqualTo(FetchOutcome.FAILED);

    verify(persister).recordFailure(eq(CANDIDATE), any(PmcSourceException.class));
    verify(persister, never()).recordFetched(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("没有 PDF 时不下载 PDF，PDF 大小为空")
  void should_skip_pdf_when_absent() throws Exception {
    PmcArticleMetadata metadata = metadata(file("PMC1.2.xml", "<a/>"), null, List.of());
    when(source.listVersionPrefixes("PMC1")).thenReturn(List.of("PMC1.2"));
    when(source.getMetadata("PMC1.2")).thenReturn(metadata);
    stubDownload("PMC1.2.xml", "<a/>");

    assertThat(worker.process(CANDIDATE)).isEqualTo(FetchOutcome.FETCHED);

    verify(persister).recordFetched(eq(CANDIDATE), eq(metadata), isNull(), eq(List.of()), eq(NOW));
  }

  @Test
  @DisplayName("访问 PMC 出错时记抓取失败")
  void should_record_failure_when_source_throws() {
    when(source.listVersionPrefixes("PMC1")).thenThrow(new PmcSourceException("down"));

    assertThat(worker.process(CANDIDATE)).isEqualTo(FetchOutcome.FAILED);

    verify(persister).recordFailure(eq(CANDIDATE), any(PmcSourceException.class));
  }

  private static PmcArticleMetadata metadata(
      PmcArticleFile xml, PmcArticleFile pdf, List<PmcArticleFile> media) {
    return PmcArticleMetadata.builder()
        .pmcid("PMC1")
        .versionPrefix("PMC1.2")
        .openAccess(true)
        .xml(xml)
        .pdf(pdf)
        .media(media)
        .build();
  }

  private static PmcArticleFile file(String name, String content) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("MD5").digest(content.getBytes(StandardCharsets.UTF_8));
    return PmcArticleFile.of(name, HexFormat.of().formatHex(digest));
  }

  private void stubDownload(String name, String content) throws Exception {
    URI uri = URI.create("https://pmc/PMC1.2/" + name);
    when(fileDownloadPort.download(uri))
        .thenAnswer(
            inv -> {
              Path path =
                  Files.writeString(tempDir.resolve(name + "-" + System.nanoTime()), content);
              return FileDownloadResult.of(path, Files.size(path));
            });
  }
}
