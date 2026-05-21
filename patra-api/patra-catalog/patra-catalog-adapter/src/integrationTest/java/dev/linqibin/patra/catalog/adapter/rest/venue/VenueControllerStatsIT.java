package dev.linqibin.patra.catalog.adapter.rest.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.adapter.config.TestConfiguration;
import dev.linqibin.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import dev.linqibin.patra.catalog.app.usecase.venue.query.VenueQueryService;
import dev.linqibin.patra.catalog.app.usecase.venue.query.dto.VenueStatsQuery;
import dev.linqibin.patra.catalog.domain.exception.VenueNotFoundException;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueStatsReadModel;
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

/// VenueController 发文统计查询 REST 接口切片测试。
///
/// **测试目标**：
///
/// - 200 OK：返回多年发文统计记录，按年份降序
/// - 404 Not Found：Venue 不存在时 VenueNotFoundException 被自动映射
/// - 路径参数绑定正确
/// - MapStruct 转换器字段映射正确（使用真实 VenueApiConverter）
@WebMvcTest(controllers = VenueController.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Import(VenueController.class)
@AutoConfigureRestTestClient
@DisplayName("VenueController 发文统计查询 REST 接口切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueControllerStatsIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueQueryService venueQueryService;
  @MockitoBean private PublicationQueryService publicationQueryService;

  /// 查询存在的 ID 应返回 200 和多年发文统计（按年份降序）。
  @Test
  @DisplayName("GET /venues/{id}/stats 应返回 200 和完整发文统计")
  void shouldReturn200WithStats() {
    // Given
    Long venueId = 1001L;
    var stats2024 = new VenueStatsReadModel.YearStats((short) 2024, 3500, 85000, 1200);
    var stats2023 = new VenueStatsReadModel.YearStats((short) 2023, 3200, 78000, 1100);
    var stats2022 = new VenueStatsReadModel.YearStats((short) 2022, 2900, 70000, null);

    var readModel = new VenueStatsReadModel(List.of(stats2024, stats2023, stats2022));

    when(venueQueryService.getVenueStats(any(VenueStatsQuery.class))).thenReturn(readModel);

    // When & Then
    restClient
        .get()
        .uri("/venues/{id}/stats", venueId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        // 3 条记录，按年份降序
        .jsonPath("$.stats.length()")
        .isEqualTo(3)
        .jsonPath("$.stats[0].year")
        .isEqualTo(2024)
        .jsonPath("$.stats[0].worksCount")
        .isEqualTo(3500)
        .jsonPath("$.stats[0].citedByCount")
        .isEqualTo(85000)
        .jsonPath("$.stats[0].oaWorksCount")
        .isEqualTo(1200)
        .jsonPath("$.stats[1].year")
        .isEqualTo(2023)
        .jsonPath("$.stats[1].worksCount")
        .isEqualTo(3200)
        .jsonPath("$.stats[1].citedByCount")
        .isEqualTo(78000)
        .jsonPath("$.stats[1].oaWorksCount")
        .isEqualTo(1100)
        .jsonPath("$.stats[2].year")
        .isEqualTo(2022)
        .jsonPath("$.stats[2].worksCount")
        .isEqualTo(2900)
        .jsonPath("$.stats[2].oaWorksCount")
        .isEmpty();

    // 验证路径参数正确绑定
    ArgumentCaptor<VenueStatsQuery> queryCaptor = ArgumentCaptor.forClass(VenueStatsQuery.class);
    verify(venueQueryService).getVenueStats(queryCaptor.capture());
    assertThat(queryCaptor.getValue().id()).isEqualTo(venueId);
  }

  /// 查询不存在的 Venue 应返回 404 Not Found。
  @Test
  @DisplayName("GET /venues/{id}/stats 不存在的 ID 应返回 404 Not Found")
  void shouldReturn404WhenVenueNotExists() {
    // Given
    Long invalidId = 999999L;
    when(venueQueryService.getVenueStats(any(VenueStatsQuery.class)))
        .thenThrow(new VenueNotFoundException(invalidId));

    // When & Then
    restClient.get().uri("/venues/{id}/stats", invalidId).exchange().expectStatus().isNotFound();

    // 验证路径参数正确绑定
    ArgumentCaptor<VenueStatsQuery> queryCaptor = ArgumentCaptor.forClass(VenueStatsQuery.class);
    verify(venueQueryService).getVenueStats(queryCaptor.capture());
    assertThat(queryCaptor.getValue().id()).isEqualTo(invalidId);
  }

  /// 查询无发文统计数据的 Venue 应返回 200 和空列表。
  @Test
  @DisplayName("GET /venues/{id}/stats 无发文数据应返回 200 和空列表")
  void shouldReturn200WithEmptyListWhenNoStatsData() {
    // Given
    Long venueId = 1002L;
    var emptyReadModel = new VenueStatsReadModel(List.of());

    when(venueQueryService.getVenueStats(any(VenueStatsQuery.class))).thenReturn(emptyReadModel);

    // When & Then
    restClient
        .get()
        .uri("/venues/{id}/stats", venueId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.stats.length()")
        .isEqualTo(0);
  }
}
