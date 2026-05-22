package dev.linqibin.patra.catalog.adapter.rest.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.linqibin.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import dev.linqibin.patra.catalog.app.usecase.publication.query.dto.TopPublicationsQuery;
import dev.linqibin.patra.catalog.app.usecase.venue.query.VenueQueryService;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
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

/// VenueController `/top-publications` 端点切片测试。
///
/// **测试目标**：
///
/// - 路径参数 `id` 正确绑定到 `TopPublicationsQuery.venueId`
/// - 无 query 参数时 `limit` 被归一化到默认值 5
/// - `limit` 显式指定时透传
/// - `limit > 20` 触发 Bean Validation 返回 400
/// - 响应字段与 `TopPublicationItemResponse` 结构一致
@WebMvcTest(controllers = VenueController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import(VenueController.class)
@AutoConfigureRestTestClient
@DisplayName("VenueController#listTopPublicationsByVenue 切片测试")
class VenueControllerTopPublicationsIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueQueryService venueQueryService;
  @MockitoBean private PublicationQueryService publicationQueryService;

  private PublicationSummaryReadModel sampleReadModel() {
    return new PublicationSummaryReadModel(
        12345L,
        "CAR-T cell therapy",
        "11111111",
        "10.1/test",
        2023,
        "en",
        false,
        "closed",
        2001L,
        "Nature Medicine",
        1247,
        Instant.parse("2026-02-13T00:00:00Z"));
  }

  @Test
  @DisplayName("GET /venues/{id}/top-publications 默认 limit 归一化为 5")
  void shouldDefaultLimitToFive() {
    when(publicationQueryService.listTopPublicationsByVenue(any(TopPublicationsQuery.class)))
        .thenReturn(List.of(sampleReadModel()));

    restClient
        .get()
        .uri("/venues/2001/top-publications")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(1)
        .jsonPath("$[0].id")
        .isEqualTo("12345")
        .jsonPath("$[0].title")
        .isEqualTo("CAR-T cell therapy")
        .jsonPath("$[0].publicationYear")
        .isEqualTo(2023)
        .jsonPath("$[0].citationCount")
        .isEqualTo(1247)
        .jsonPath("$[0].doi")
        .isEqualTo("10.1/test");

    ArgumentCaptor<TopPublicationsQuery> captor =
        ArgumentCaptor.forClass(TopPublicationsQuery.class);
    verify(publicationQueryService).listTopPublicationsByVenue(captor.capture());
    TopPublicationsQuery query = captor.getValue();
    assertThat(query.venueId()).isEqualTo(2001L);
    assertThat(query.limit()).isEqualTo(5);
    assertThat(query.since()).isNull();
  }

  @Test
  @DisplayName("limit=3 & since=2020 透传到 Query")
  void shouldPassLimitAndSince() {
    when(publicationQueryService.listTopPublicationsByVenue(any(TopPublicationsQuery.class)))
        .thenReturn(List.of());

    restClient
        .get()
        .uri("/venues/2001/top-publications?limit=3&since=2020")
        .exchange()
        .expectStatus()
        .isOk();

    ArgumentCaptor<TopPublicationsQuery> captor =
        ArgumentCaptor.forClass(TopPublicationsQuery.class);
    verify(publicationQueryService).listTopPublicationsByVenue(captor.capture());
    TopPublicationsQuery query = captor.getValue();
    assertThat(query.limit()).isEqualTo(3);
    assertThat(query.since()).isEqualTo(2020);
  }

  @Test
  @DisplayName("limit=50 超出上限时被 TopPublicationsQuery.of 钳位为 20")
  void shouldClampLimitAboveMax() {
    when(publicationQueryService.listTopPublicationsByVenue(any(TopPublicationsQuery.class)))
        .thenReturn(List.of());

    restClient.get().uri("/venues/2001/top-publications?limit=50").exchange().expectStatus().isOk();

    ArgumentCaptor<TopPublicationsQuery> captor =
        ArgumentCaptor.forClass(TopPublicationsQuery.class);
    verify(publicationQueryService).listTopPublicationsByVenue(captor.capture());
    assertThat(captor.getValue().limit()).isEqualTo(20);
  }

  @Test
  @DisplayName("limit=0 低于下限时被钳位为 1")
  void shouldClampLimitBelowMin() {
    when(publicationQueryService.listTopPublicationsByVenue(any(TopPublicationsQuery.class)))
        .thenReturn(List.of());

    restClient.get().uri("/venues/2001/top-publications?limit=0").exchange().expectStatus().isOk();

    ArgumentCaptor<TopPublicationsQuery> captor =
        ArgumentCaptor.forClass(TopPublicationsQuery.class);
    verify(publicationQueryService).listTopPublicationsByVenue(captor.capture());
    assertThat(captor.getValue().limit()).isEqualTo(1);
  }
}
