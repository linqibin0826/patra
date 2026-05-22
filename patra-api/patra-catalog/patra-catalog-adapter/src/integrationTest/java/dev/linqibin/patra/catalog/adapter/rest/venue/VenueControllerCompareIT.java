package dev.linqibin.patra.catalog.adapter.rest.venue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.linqibin.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import dev.linqibin.patra.catalog.app.usecase.venue.query.VenueQueryService;
import dev.linqibin.patra.catalog.app.usecase.venue.query.dto.VenueCompareQuery;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueLatestRating;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// VenueController 期刊对比查询 REST 接口切片测试。
///
/// **测试目标**：
///
/// - 200 OK：返回多个 Venue 详情用于对比
/// - 参数绑定：`ids` 查询参数正确解析为 List
/// - 不存在的 ID 被静默忽略
/// - 参数校验：数量不足或超出限制时返回错误
@WebMvcTest(controllers = VenueController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import(VenueController.class)
@AutoConfigureRestTestClient
@DisplayName("VenueController 期刊对比查询 REST 接口切片测试")
class VenueControllerCompareIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueQueryService venueQueryService;

  @MockitoBean private PublicationQueryService publicationQueryService;

  /// 对比 3 本期刊应返回 200 和各自详情（含最新评级）。
  @Test
  @DisplayName("GET /venues/compare?ids=1,2,3 应返回 200 和 3 本期刊详情")
  void shouldReturn200WithThreeVenueDetails() {
    // Given
    var now = Instant.now();
    var venue1 = buildDetailReadModel(1L, "Nature", "Q1", new BigDecimal("69.504"), now);
    var venue2 = buildDetailReadModel(2L, "Science", "Q1", new BigDecimal("56.9"), now);
    var venue3 = buildDetailReadModel(3L, "Cell", "Q1", new BigDecimal("64.5"), now);

    when(venueQueryService.compareVenues(any(VenueCompareQuery.class)))
        .thenReturn(List.of(venue1, venue2, venue3));

    // When & Then
    restClient
        .get()
        .uri("/venues/compare?ids=1,2,3")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(3)
        .jsonPath("$[0].id")
        .isEqualTo("1")
        .jsonPath("$[0].title")
        .isEqualTo("Nature")
        .jsonPath("$[0].latestRating.jifQuartile")
        .isEqualTo("Q1")
        .jsonPath("$[0].latestRating.impactFactor")
        .isEqualTo(69.504)
        .jsonPath("$[1].id")
        .isEqualTo("2")
        .jsonPath("$[1].title")
        .isEqualTo("Science")
        .jsonPath("$[2].id")
        .isEqualTo("3")
        .jsonPath("$[2].title")
        .isEqualTo("Cell");

    // 验证 QueryService 接收到正确的查询参数
    ArgumentCaptor<VenueCompareQuery> queryCaptor =
        ArgumentCaptor.forClass(VenueCompareQuery.class);
    verify(venueQueryService).compareVenues(queryCaptor.capture());
    var capturedIds = queryCaptor.getValue().ids();
    org.assertj.core.api.Assertions.assertThat(capturedIds).containsExactly(1L, 2L, 3L);
  }

  /// 混入不存在的 ID 时，仅返回查到的期刊。
  @Test
  @DisplayName("GET /venues/compare 混入不存在 ID 应仅返回查到的期刊")
  void shouldReturnOnlyFoundVenuesWhenSomeIdsNotExist() {
    // Given
    var now = Instant.now();
    var venue1 = buildDetailReadModel(1L, "Nature", "Q1", new BigDecimal("69.504"), now);

    when(venueQueryService.compareVenues(any(VenueCompareQuery.class))).thenReturn(List.of(venue1));

    // When & Then
    restClient
        .get()
        .uri("/venues/compare?ids=1,999999")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(1)
        .jsonPath("$[0].id")
        .isEqualTo("1")
        .jsonPath("$[0].title")
        .isEqualTo("Nature");
  }

  /// ID 数量不足 2 个时应返回 422（VenueCompareQuery 校验失败抛出 IllegalArgumentException，
  /// 由 DefaultErrorResolutionEngine 映射为 422 Unprocessable Entity）。
  @Test
  @DisplayName("GET /venues/compare?ids=1 应返回 422（数量不足）")
  void shouldReturn422WhenLessThanTwoIds() {
    // When & Then
    restClient.get().uri("/venues/compare?ids=1").exchange().expectStatus().isEqualTo(422);
  }

  /// ID 数量超过 5 个时应返回 422（VenueCompareQuery 校验失败抛出 IllegalArgumentException，
  /// 由 DefaultErrorResolutionEngine 映射为 422 Unprocessable Entity）。
  @Test
  @DisplayName("GET /venues/compare?ids=1,2,3,4,5,6 应返回 422（数量超限）")
  void shouldReturn422WhenMoreThanFiveIds() {
    // When & Then
    restClient
        .get()
        .uri("/venues/compare?ids=1,2,3,4,5,6")
        .exchange()
        .expectStatus()
        .isEqualTo(422);
  }

  /// 构建测试用 VenueDetailReadModel。
  private VenueDetailReadModel buildDetailReadModel(
      Long id, String title, String jifQuartile, BigDecimal impactFactor, Instant now) {
    var latestRating =
        VenueLatestRating.builder()
            .jcrYear((short) 2025)
            .impactFactor(impactFactor)
            .jifQuartile(jifQuartile)
            .build();

    return VenueDetailReadModel.builder()
        .id(id)
        .venueType("JOURNAL")
        .title(title)
        .latestRating(latestRating)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
