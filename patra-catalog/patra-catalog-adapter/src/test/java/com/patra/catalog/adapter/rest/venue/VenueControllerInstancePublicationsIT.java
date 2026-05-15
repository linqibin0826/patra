package com.patra.catalog.adapter.rest.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.adapter.config.TestConfiguration;
import com.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import com.patra.catalog.app.usecase.publication.query.dto.PublicationListQuery;
import com.patra.catalog.app.usecase.venue.query.VenueQueryService;
import com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import dev.linqibin.commons.query.PageResult;
import java.time.Instant;
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

/// VenueController 实例文献列表端点切片测试。
///
/// **测试目标**：
///
/// - 路径参数（venueId、instanceId）正确绑定
/// - 查询参数（page、pageSize、sortBy）正确透传
/// - 控制器委托链路正确（Controller -> PublicationQueryService -> PublicationApiConverter）
/// - 响应结构与字段序列化正确
@WebMvcTest(controllers = VenueController.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Import(VenueController.class)
@AutoConfigureRestTestClient
@DisplayName("VenueController 实例文献列表端点切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueControllerInstancePublicationsIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueQueryService venueQueryService;
  @MockitoBean private PublicationQueryService publicationQueryService;

  /// 指定实例 ID 时应返回该实例下的文献分页结果。
  @Test
  @DisplayName("GET /venues/{venueId}/instances/{instanceId}/publications 应返回 200 和分页数据")
  void shouldReturnPagedPublicationsForInstance() {
    // Given
    var readModel =
        new PublicationSummaryReadModel(
            2001L,
            "Test Article",
            "12345678",
            "10.1234/test",
            2024,
            "en",
            false,
            "closed",
            100L,
            "Nature",
            42,
            Instant.parse("2026-02-13T00:00:00Z"));
    PageResult<PublicationSummaryReadModel> serviceResult =
        PageResult.of(List.of(readModel), 1, 20, 1);

    when(publicationQueryService.listPublications(any(PublicationListQuery.class)))
        .thenReturn(serviceResult);

    // When & Then
    restClient
        .get()
        .uri("/venues/100/instances/200/publications?page=1&pageSize=20")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.page")
        .isEqualTo(1)
        .jsonPath("$.pageSize")
        .isEqualTo(20)
        .jsonPath("$.total")
        .isEqualTo(1)
        .jsonPath("$.items.length()")
        .isEqualTo(1)
        .jsonPath("$.items[0].id")
        .isEqualTo("2001")
        .jsonPath("$.items[0].title")
        .isEqualTo("Test Article")
        .jsonPath("$.items[0].pmid")
        .isEqualTo("12345678")
        .jsonPath("$.items[0].citationCount")
        .isEqualTo(42);

    // 验证 QueryService 接收到正确的查询参数
    ArgumentCaptor<PublicationListQuery> queryCaptor =
        ArgumentCaptor.forClass(PublicationListQuery.class);
    verify(publicationQueryService).listPublications(queryCaptor.capture());
    PublicationListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.venueId()).isEqualTo(100L);
    assertThat(capturedQuery.venueInstanceId()).isEqualTo(200L);
    assertThat(capturedQuery.page()).isEqualTo(1);
    assertThat(capturedQuery.pageSize()).isEqualTo(20);
    assertThat(capturedQuery.sortBy()).isNull();
  }

  /// sortBy 参数应正确透传到查询对象。
  @Test
  @DisplayName(
      "GET /venues/{venueId}/instances/{instanceId}/publications?sortBy=citedByCount 应透传排序参数")
  void shouldPassSortByParameter() {
    // Given
    PageResult<PublicationSummaryReadModel> serviceResult = PageResult.empty(1, 20);
    when(publicationQueryService.listPublications(any(PublicationListQuery.class)))
        .thenReturn(serviceResult);

    // When
    restClient
        .get()
        .uri("/venues/100/instances/200/publications?sortBy=citedByCount")
        .exchange()
        .expectStatus()
        .isOk();

    // Then
    ArgumentCaptor<PublicationListQuery> queryCaptor =
        ArgumentCaptor.forClass(PublicationListQuery.class);
    verify(publicationQueryService).listPublications(queryCaptor.capture());
    PublicationListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.sortBy()).isEqualTo("citedByCount");
    assertThat(capturedQuery.venueId()).isEqualTo(100L);
    assertThat(capturedQuery.venueInstanceId()).isEqualTo(200L);
  }

  /// 无查询参数时应使用 null 分页参数。
  @Test
  @DisplayName("GET /venues/{venueId}/instances/{instanceId}/publications 无参数时 null 应透传到服务层")
  void shouldUseNullParamsWhenNotProvided() {
    // Given
    PageResult<PublicationSummaryReadModel> serviceResult = PageResult.empty(1, 20);
    when(publicationQueryService.listPublications(any(PublicationListQuery.class)))
        .thenReturn(serviceResult);

    // When
    restClient.get().uri("/venues/100/instances/200/publications").exchange().expectStatus().isOk();

    // Then
    ArgumentCaptor<PublicationListQuery> queryCaptor =
        ArgumentCaptor.forClass(PublicationListQuery.class);
    verify(publicationQueryService).listPublications(queryCaptor.capture());
    PublicationListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.page()).isNull();
    assertThat(capturedQuery.pageSize()).isNull();
    assertThat(capturedQuery.sortBy()).isNull();
    assertThat(capturedQuery.venueId()).isEqualTo(100L);
    assertThat(capturedQuery.venueInstanceId()).isEqualTo(200L);
  }
}
