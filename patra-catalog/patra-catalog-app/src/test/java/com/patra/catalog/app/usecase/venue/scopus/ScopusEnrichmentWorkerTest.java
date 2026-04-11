package com.patra.catalog.app.usecase.venue.scopus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.scopus.ScopusEnrichmentWorker.Outcome;
import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData.YearlyMetric;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ScopusEnrichmentWorker 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class ScopusEnrichmentWorkerTest {

  @Mock ScopusEnrichmentPort scraperPort;
  @Mock ScopusEnrichmentPersistPort persistPort;

  ScopusEnrichmentWorker worker;

  @BeforeEach
  void setUp() {
    worker = new ScopusEnrichmentWorker(scraperPort, persistPort);
  }

  @Test
  @DisplayName("Happy path - 爬取 + 持久化成功")
  void processVenue_happyPath_returnsPROCESSED() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
    ScopusVenueData data = scopusDataWithOneYear();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(persistPort.persist(100L, data))
        .thenReturn(ScopusEnrichmentPersistPort.PersistStats.of(1));

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.PROCESSED);
    verify(persistPort).persist(100L, data);
  }

  @Test
  @DisplayName("ISSN-L 为空 - 直接返回 MISSING_ISSN，不调任何 port")
  void processVenue_missingIssn_returnsMISSING_ISSN() {
    VenueSnapshot v = VenueSnapshot.of(100L, null);

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.MISSING_ISSN);
    verifyNoInteractions(scraperPort, persistPort);
  }

  @Test
  @DisplayName("ISSN-L 为空白字符串 - 直接返回 MISSING_ISSN，不调任何 port")
  void processVenue_blankIssn_returnsMISSING_ISSN() {
    VenueSnapshot v = VenueSnapshot.of(100L, "   ");

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.MISSING_ISSN);
    verifyNoInteractions(scraperPort, persistPort);
  }

  @Test
  @DisplayName("Scopus 未找到数据 - 返回 NOT_FOUND_IN_SOURCE，不走持久化")
  void processVenue_notFoundOnSource_returnsNOT_FOUND_IN_SOURCE() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.empty());

    Outcome outcome = worker.processVenue(v);

    assertThat(outcome).isEqualTo(Outcome.NOT_FOUND_IN_SOURCE);
    verifyNoInteractions(persistPort);
  }

  @Test
  @DisplayName("爬虫抛异常 - 向上传播，由 Runner 层兜底")
  void processVenue_scraperThrows_propagatesException() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
    when(scraperPort.findByIssn("1234-5678")).thenThrow(new RuntimeException("scopus api down"));

    assertThatThrownBy(() -> worker.processVenue(v))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("scopus api down");
  }

  @Test
  @DisplayName("persistPort 抛异常 - 向上传播，事务回滚")
  void processVenue_persistThrows_propagatesException() {
    VenueSnapshot v = VenueSnapshot.of(100L, "1234-5678");
    ScopusVenueData data = scopusDataWithOneYear();
    when(scraperPort.findByIssn("1234-5678")).thenReturn(Optional.of(data));
    when(persistPort.persist(100L, data))
        .thenThrow(new RuntimeException("db constraint violation"));

    assertThatThrownBy(() -> worker.processVenue(v))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db constraint violation");
  }

  // ========== Fixture 构造器 ==========

  /// 构造含单年 yearlyMetrics 的最小 ScopusVenueData。
  /// Worker 不关心 Mapper 输出——那是 PersistPort 的契约——所以数据结构只需
  /// 通过 record 构造器即可，不必填充所有字段。
  private ScopusVenueData scopusDataWithOneYear() {
    return ScopusVenueData.builder()
        .scopusSourceId("21100")
        .title("Test Journal")
        .yearlyMetrics(List.of(new YearlyMetric(2025, 5.0, 100, 500, 80.0)))
        .build();
  }
}
