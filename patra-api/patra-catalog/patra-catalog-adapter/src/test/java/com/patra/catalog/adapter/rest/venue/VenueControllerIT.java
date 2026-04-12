package com.patra.catalog.adapter.rest.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.adapter.config.TestConfiguration;
import com.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import com.patra.catalog.app.usecase.venue.query.VenueQueryService;
import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.common.query.PageResult;
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

/// VenueController REST 接口切片测试。
///
/// **测试目标**：
///
/// - 路径与查询参数绑定正确
/// - 控制器委托链路正确（Controller -> Converter -> QueryService）
/// - MapStruct 转换器字段映射正确（使用真实 VenueApiConverter）
/// - 响应结构与字段序列化正确
@WebMvcTest(controllers = VenueController.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Import(VenueController.class)
@AutoConfigureRestTestClient
@DisplayName("VenueController REST 接口切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueQueryService venueQueryService;
  @MockitoBean private PublicationQueryService publicationQueryService;

  /// 指定查询参数时应返回分页结果。
  @Test
  @DisplayName("GET /venues 指定参数应返回 200 和分页数据")
  void shouldReturnPagedVenuesWhenQueryProvided() {
    // Given
    VenueSummaryReadModel readModel =
        VenueSummaryReadModel.builder()
            .id(1001L)
            .title("Nature")
            .countryCode("US")
            .imageObjectKey("catalog/venue-cover/1001.jpg")
            .hIndex(412)
            .jifQuartile("Q1")
            .casMajorQuartile("1区")
            .casTopJournal(true)
            .isOa(false)
            .build();
    PageResult<VenueSummaryReadModel> serviceResult = PageResult.of(List.of(readModel), 2, 10, 31);

    when(venueQueryService.listVenues(any(VenueListQuery.class))).thenReturn(serviceResult);

    // When & Then
    restClient
        .get()
        .uri("/venues?page=2&pageSize=10&q=Nature")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.page")
        .isEqualTo(2)
        .jsonPath("$.pageSize")
        .isEqualTo(10)
        .jsonPath("$.total")
        .isEqualTo(31)
        .jsonPath("$.totalPages")
        .isEqualTo(4)
        .jsonPath("$.items.length()")
        .isEqualTo(1)
        .jsonPath("$.items[0].id")
        .isEqualTo("1001")
        .jsonPath("$.items[0].title")
        .isEqualTo("Nature")
        .jsonPath("$.items[0].countryCode")
        .isEqualTo("US")
        .jsonPath("$.items[0].imageObjectKey")
        .isEqualTo("catalog/venue-cover/1001.jpg")
        .jsonPath("$.items[0].hIndex")
        .isEqualTo(412)
        .jsonPath("$.items[0].jifQuartile")
        .isEqualTo("Q1")
        .jsonPath("$.items[0].casMajorQuartile")
        .isEqualTo("1区")
        .jsonPath("$.items[0].casTopJournal")
        .isEqualTo(true)
        .jsonPath("$.items[0].isOa")
        .isEqualTo(false);

    // 验证 QueryService 接收到正确的查询参数（由真实 Converter 转换）
    ArgumentCaptor<VenueListQuery> queryCaptor = ArgumentCaptor.forClass(VenueListQuery.class);
    verify(venueQueryService).listVenues(queryCaptor.capture());
    VenueListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.page()).isEqualTo(2);
    assertThat(capturedQuery.pageSize()).isEqualTo(10);
    assertThat(capturedQuery.q()).isEqualTo("Nature");
  }

  /// 未指定查询参数时应使用默认分页参数。
  @Test
  @DisplayName("GET /venues 无参数时 null 应透传到服务层归一化")
  void shouldUseDefaultParamsWhenQueryNotProvided() {
    // Given
    PageResult<VenueSummaryReadModel> serviceResult = PageResult.empty(1, 20);

    when(venueQueryService.listVenues(any(VenueListQuery.class))).thenReturn(serviceResult);

    // When
    restClient
        .get()
        .uri("/venues")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.page")
        .isEqualTo(1)
        .jsonPath("$.pageSize")
        .isEqualTo(20)
        .jsonPath("$.items.length()")
        .isEqualTo(0);

    // Then — 验证 Converter 正确地将空请求转换为全 null 查询
    ArgumentCaptor<VenueListQuery> queryCaptor = ArgumentCaptor.forClass(VenueListQuery.class);
    verify(venueQueryService).listVenues(queryCaptor.capture());
    VenueListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.page()).isNull();
    assertThat(capturedQuery.pageSize()).isNull();
    assertThat(capturedQuery.q()).isNull();
  }

  /// 非法分页参数不应在控制器层被拦截，需继续传递到 QueryService 做归一化。
  @Test
  @DisplayName("GET /venues 非法分页参数应继续委托到服务层")
  void shouldDelegateWhenInvalidPagingParamsProvided() {
    // Given
    PageResult<VenueSummaryReadModel> serviceResult = PageResult.empty(1, 100);

    when(venueQueryService.listVenues(any(VenueListQuery.class))).thenReturn(serviceResult);

    // When
    restClient
        .get()
        .uri("/venues?page=0&pageSize=1000&q=Nature")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.pageSize")
        .isEqualTo(100);

    // Then — 验证 Converter 忠实地传递了原始非法参数
    ArgumentCaptor<VenueListQuery> queryCaptor = ArgumentCaptor.forClass(VenueListQuery.class);
    verify(venueQueryService).listVenues(queryCaptor.capture());
    VenueListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.page()).isEqualTo(0);
    assertThat(capturedQuery.pageSize()).isEqualTo(1000);
    assertThat(capturedQuery.q()).isEqualTo("Nature");
  }
}
