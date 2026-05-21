package dev.linqibin.patra.catalog.adapter.rest.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.adapter.config.TestConfiguration;
import dev.linqibin.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import dev.linqibin.patra.catalog.app.usecase.venue.query.VenueQueryService;
import dev.linqibin.patra.catalog.app.usecase.venue.query.dto.VenueRatingHistoryQuery;
import dev.linqibin.patra.catalog.domain.exception.VenueNotFoundException;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueRatingHistoryReadModel;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// VenueController 评级历史查询 REST 接口切片测试。
///
/// **测试目标**：
///
/// - 200 OK：返回多年 JCR/CAS/Scopus/Warning 记录，按年份降序
/// - 404 Not Found：Venue 不存在时 VenueNotFoundException 被自动映射
/// - 路径参数绑定正确
/// - MapStruct 转换器字段映射正确（使用真实 VenueApiConverter）
@WebMvcTest(controllers = VenueController.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Import(VenueController.class)
@AutoConfigureRestTestClient
@DisplayName("VenueController 评级历史查询 REST 接口切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueControllerRatingHistoryIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueQueryService venueQueryService;
  @MockitoBean private PublicationQueryService publicationQueryService;

  /// 查询存在的 ID 应返回 200 和多年评级历史（按年份降序）。
  @Test
  @DisplayName("GET /venues/{id}/ratings 应返回 200 和完整评级历史")
  void shouldReturn200WithRatingHistory() {
    // Given
    Long venueId = 1001L;
    var jcr2024 =
        new VenueRatingHistoryReadModel.JcrRecord(
            (short) 2024,
            new BigDecimal("50.5000"),
            "Q1",
            "2/136",
            new BigDecimal("99.00"),
            new BigDecimal("1.60"),
            "SCIE");
    var jcr2023 =
        new VenueRatingHistoryReadModel.JcrRecord(
            (short) 2023,
            new BigDecimal("46.2000"),
            "Q1",
            "3/136",
            new BigDecimal("98.50"),
            null,
            "SCIE");

    var cas2024 =
        new VenueRatingHistoryReadModel.CasRecord(
            (short) 2024, "升级版", "医学", "1区", "肿瘤学", "1区", true, false);
    var cas2023 =
        new VenueRatingHistoryReadModel.CasRecord(
            (short) 2023, "升级版", "医学", "1区", "肿瘤学", "2区", true, false);

    var scopus2024 =
        new VenueRatingHistoryReadModel.ScopusRecord(
            (short) 2024,
            new BigDecimal("65.1000"),
            new BigDecimal("15.2000"),
            new BigDecimal("20.5000"),
            "Q1",
            new BigDecimal("99.00"),
            3000,
            50000);
    var scopus2023 =
        new VenueRatingHistoryReadModel.ScopusRecord(
            (short) 2023,
            new BigDecimal("60.0000"),
            null,
            null,
            "Q1",
            new BigDecimal("98.50"),
            2800,
            45000);

    var warning2025 =
        new VenueRatingHistoryReadModel.WarningRecord((short) 2025, "2025版", false, null);
    var warning2024 =
        new VenueRatingHistoryReadModel.WarningRecord((short) 2024, "2024版", true, "high");

    var readModel =
        VenueRatingHistoryReadModel.builder()
            .jcr(List.of(jcr2024, jcr2023))
            .cas(List.of(cas2024, cas2023))
            .scopus(List.of(scopus2024, scopus2023))
            .warnings(List.of(warning2025, warning2024))
            .build();

    when(venueQueryService.getVenueRatingHistory(any(VenueRatingHistoryQuery.class)))
        .thenReturn(readModel);

    // When & Then
    restClient
        .get()
        .uri("/venues/{id}/ratings", venueId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        // JCR: 2 条记录，按年份降序
        .jsonPath("$.jcr.length()")
        .isEqualTo(2)
        .jsonPath("$.jcr[0].year")
        .isEqualTo(2024)
        .jsonPath("$.jcr[0].impactFactor")
        .isEqualTo(50.5)
        .jsonPath("$.jcr[0].jifQuartile")
        .isEqualTo("Q1")
        .jsonPath("$.jcr[0].jifRank")
        .isEqualTo("2/136")
        .jsonPath("$.jcr[0].selfCitationRate")
        .isEqualTo(1.6)
        .jsonPath("$.jcr[0].collection")
        .isEqualTo("SCIE")
        .jsonPath("$.jcr[1].year")
        .isEqualTo(2023)
        .jsonPath("$.jcr[1].impactFactor")
        .isEqualTo(46.2)
        // CAS: 2 条记录
        .jsonPath("$.cas.length()")
        .isEqualTo(2)
        .jsonPath("$.cas[0].year")
        .isEqualTo(2024)
        .jsonPath("$.cas[0].edition")
        .isEqualTo("升级版")
        .jsonPath("$.cas[0].majorCategory")
        .isEqualTo("医学")
        .jsonPath("$.cas[0].majorQuartile")
        .isEqualTo("1区")
        .jsonPath("$.cas[0].isTopJournal")
        .isEqualTo(true)
        .jsonPath("$.cas[1].year")
        .isEqualTo(2023)
        // Scopus: 2 条记录
        .jsonPath("$.scopus.length()")
        .isEqualTo(2)
        .jsonPath("$.scopus[0].year")
        .isEqualTo(2024)
        .jsonPath("$.scopus[0].citeScore")
        .isEqualTo(65.1)
        .jsonPath("$.scopus[0].quartile")
        .isEqualTo("Q1")
        .jsonPath("$.scopus[0].documentCount")
        .isEqualTo(3000)
        .jsonPath("$.scopus[1].year")
        .isEqualTo(2023)
        // Warnings: 2 条记录
        .jsonPath("$.warnings.length()")
        .isEqualTo(2)
        .jsonPath("$.warnings[0].publishedYear")
        .isEqualTo(2025)
        .jsonPath("$.warnings[0].editionLabel")
        .isEqualTo("2025版")
        .jsonPath("$.warnings[0].inWarningList")
        .isEqualTo(false)
        .jsonPath("$.warnings[0].warningLevel")
        .isEmpty()
        .jsonPath("$.warnings[1].publishedYear")
        .isEqualTo(2024)
        .jsonPath("$.warnings[1].inWarningList")
        .isEqualTo(true)
        .jsonPath("$.warnings[1].warningLevel")
        .isEqualTo("high");

    // 验证路径参数正确绑定
    ArgumentCaptor<VenueRatingHistoryQuery> queryCaptor =
        ArgumentCaptor.forClass(VenueRatingHistoryQuery.class);
    verify(venueQueryService).getVenueRatingHistory(queryCaptor.capture());
    assertThat(queryCaptor.getValue().id()).isEqualTo(venueId);
  }

  /// 查询不存在的 Venue 应返回 404 Not Found。
  @Test
  @DisplayName("GET /venues/{id}/ratings 不存在的 ID 应返回 404 Not Found")
  void shouldReturn404WhenVenueNotExists() {
    // Given
    Long invalidId = 999999L;
    when(venueQueryService.getVenueRatingHistory(any(VenueRatingHistoryQuery.class)))
        .thenThrow(new VenueNotFoundException(invalidId));

    // When & Then
    restClient.get().uri("/venues/{id}/ratings", invalidId).exchange().expectStatus().isNotFound();

    // 验证路径参数正确绑定
    ArgumentCaptor<VenueRatingHistoryQuery> queryCaptor =
        ArgumentCaptor.forClass(VenueRatingHistoryQuery.class);
    verify(venueQueryService).getVenueRatingHistory(queryCaptor.capture());
    assertThat(queryCaptor.getValue().id()).isEqualTo(invalidId);
  }

  /// 查询无评级数据的 Venue 应返回 200 和空列表。
  @Test
  @DisplayName("GET /venues/{id}/ratings 无评级数据应返回 200 和空列表")
  void shouldReturn200WithEmptyListsWhenNoRatingData() {
    // Given
    Long venueId = 1002L;
    var emptyReadModel =
        VenueRatingHistoryReadModel.builder()
            .jcr(List.of())
            .cas(List.of())
            .scopus(List.of())
            .warnings(List.of())
            .build();

    when(venueQueryService.getVenueRatingHistory(any(VenueRatingHistoryQuery.class)))
        .thenReturn(emptyReadModel);

    // When & Then
    restClient
        .get()
        .uri("/venues/{id}/ratings", venueId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.jcr.length()")
        .isEqualTo(0)
        .jsonPath("$.cas.length()")
        .isEqualTo(0)
        .jsonPath("$.scopus.length()")
        .isEqualTo(0)
        .jsonPath("$.warnings.length()")
        .isEqualTo(0);
  }
}
