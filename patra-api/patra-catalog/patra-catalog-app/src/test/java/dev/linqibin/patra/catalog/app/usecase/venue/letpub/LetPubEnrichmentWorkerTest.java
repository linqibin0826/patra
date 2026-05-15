package dev.linqibin.patra.catalog.app.usecase.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentWorker.Outcome;
import dev.linqibin.patra.catalog.domain.exception.FileDownloadException;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubVenueData;
import dev.linqibin.patra.catalog.domain.port.enrichment.VenueSnapshot;
import dev.linqibin.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("LetPubEnrichmentWorker 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class LetPubEnrichmentWorkerTest {

  @Mock LetPubEnrichmentPort scraperPort;
  @Mock LetPubEnrichmentPersister persister;
  @Mock VenueCoverImageDownloadPort coverPort;

  LetPubEnrichmentWorker worker;

  @BeforeEach
  void setUp() {
    worker = new LetPubEnrichmentWorker(scraperPort, persister, coverPort);
  }

  @Test
  @DisplayName("Happy path - 爬虫+封面+持久化全部成功")
  void processVenue_happyPath_returnsPROCESSED() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678", null);
    LetPubVenueData data = letPubDataWithCoverUrl();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(coverPort.downloadAndStore(any(), eq("catalog/venue-cover/100.jpg")))
        .thenReturn("catalog/venue-cover/100.jpg");
    when(persister.persist(100L, data, "catalog/venue-cover/100.jpg"))
        .thenReturn(new LetPubEnrichmentPersistPort.PersistStats(10, 5, 2, true));

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.PROCESSED);
    verify(persister).persist(100L, data, "catalog/venue-cover/100.jpg");
  }

  @Test
  @DisplayName("ISSN-L 为空 - 直接返回 MISSING_ISSN，不调任何 port")
  void processVenue_missingIssn_returnsMISSING_ISSN() {
    VenueSnapshot v = VenueSnapshot.of(100L, null, null);

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.MISSING_ISSN);
    verifyNoInteractions(scraperPort, persister, coverPort);
  }

  @Test
  @DisplayName("LetPub 未找到数据 - 返回 NOT_FOUND_IN_SOURCE，不走持久化")
  void processVenue_notFoundOnSource_returnsNOT_FOUND_IN_SOURCE() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678", null);
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.empty());

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.NOT_FOUND_IN_SOURCE);
    verifyNoInteractions(persister, coverPort);
  }

  @Test
  @DisplayName("封面下载失败 - 继续主流程，coverObjectKey=null 传给 persistPort")
  void processVenue_coverDownloadFails_continuesWithoutCover() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678", null);
    LetPubVenueData data = letPubDataWithCoverUrl();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(coverPort.downloadAndStore(any(), any()))
        .thenThrow(new FileDownloadException("network", StandardErrorTrait.DEP_UNAVAILABLE));
    when(persister.persist(eq(100L), eq(data), isNull()))
        .thenReturn(new LetPubEnrichmentPersistPort.PersistStats(1, 0, 0, false));

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.PROCESSED);
    verify(persister).persist(100L, data, null);
  }

  @Test
  @DisplayName("爬虫抛异常 - 向上传播，由 Runner 层的 try/catch 兜底")
  void processVenue_scraperThrows_propagatesException() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678", null);
    when(scraperPort.findByIssn("1234-5678")).thenThrow(new RuntimeException("rate limited"));

    assertThatThrownBy(() -> worker.processVenue(v))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("rate limited");
  }

  @Test
  @DisplayName("Venue 已存在封面键 - 跳过下载，persist 收到 null cover key")
  void processVenue_existingCoverKey_skipsDownload() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678", "catalog/venue-cover/100.jpg");
    LetPubVenueData data = letPubDataWithCoverUrl();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(persister.persist(eq(100L), eq(data), isNull()))
        .thenReturn(new LetPubEnrichmentPersistPort.PersistStats(1, 0, 0, false));

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.PROCESSED);
    verifyNoInteractions(coverPort);
    verify(persister).persist(100L, data, null);
  }

  @Test
  @DisplayName("LetPub 未返回封面 URL - 跳过下载，persist 收到 null cover key")
  void processVenue_blankCoverUrl_skipsDownload() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678", null);
    LetPubVenueData data = letPubDataWithoutCoverUrl();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(persister.persist(eq(100L), eq(data), isNull()))
        .thenReturn(new LetPubEnrichmentPersistPort.PersistStats(1, 0, 0, false));

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.PROCESSED);
    verifyNoInteractions(coverPort);
    verify(persister).persist(100L, data, null);
  }

  @Test
  @DisplayName("persistPort 抛异常 - 向上传播，事务回滚")
  void processVenue_persistThrows_propagatesException() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678", null);
    LetPubVenueData data = letPubDataWithCoverUrl();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(coverPort.downloadAndStore(any(), any())).thenReturn("catalog/venue-cover/100.jpg");
    when(persister.persist(100L, data, "catalog/venue-cover/100.jpg"))
        .thenThrow(new RuntimeException("db constraint violation"));

    assertThatThrownBy(() -> worker.processVenue(v))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db constraint violation");
  }

  // ========== Fixture 构造器（inline） ==========

  /// 构造含封面 URL 的最小 LetPubVenueData。
  ///
  /// 仅填充 `basicInfo.letPubJournalId` 和 `basicInfo.coverImageSourceUrl`，
  /// JCR/CAS/Warning 为空列表（Worker 不关心 Mapper 输出细节，那是 persistPort 的契约）。
  private LetPubVenueData letPubDataWithCoverUrl() {
    return LetPubVenueData.of(
        LetPubVenueData.BasicInfo.builder()
            .letPubJournalId("1")
            .letPubName("Test Journal")
            .coverImageSourceUrl("https://letpub.example.com/cover/1.jpg")
            .build(),
        LetPubVenueData.JcrMetrics.builder().impactFactorTrend(Map.of("2024-2025", 50.0)).build(),
        LetPubVenueData.CasData.of(List.of(), List.of()),
        null);
  }

  /// 构造不含封面 URL 的最小 LetPubVenueData，用于验证"blank URL 跳过下载"分支。
  private LetPubVenueData letPubDataWithoutCoverUrl() {
    return LetPubVenueData.of(
        LetPubVenueData.BasicInfo.builder()
            .letPubJournalId("1")
            .letPubName("Test Journal")
            // 注意：不设 coverImageSourceUrl
            .build(),
        LetPubVenueData.JcrMetrics.builder().impactFactorTrend(Map.of("2024-2025", 50.0)).build(),
        LetPubVenueData.CasData.of(List.of(), List.of()),
        null);
  }
}
