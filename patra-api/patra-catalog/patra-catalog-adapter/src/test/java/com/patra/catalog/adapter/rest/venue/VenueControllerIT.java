package com.patra.catalog.adapter.rest.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.adapter.config.TestConfiguration;
import com.patra.catalog.adapter.rest.venue.mapper.VenueApiConverter;
import com.patra.catalog.adapter.rest.venue.request.VenueListRequest;
import com.patra.catalog.adapter.rest.venue.response.VenueItemResponse;
import com.patra.catalog.app.usecase.venue.query.VenueQueryService;
import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.common.query.PageResult;
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
/// - 响应结构与字段序列化正确
@WebMvcTest(controllers = VenueController.class)
@ContextConfiguration(classes = TestConfiguration.class)
@AutoConfigureRestTestClient
@DisplayName("VenueController REST 接口切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueQueryService venueQueryService;

  @MockitoBean private VenueApiConverter venueApiConverter;

  /// 指定查询参数时应返回分页结果。
  @Test
  @DisplayName("GET /venues 指定参数应返回 200 和分页数据")
  void shouldReturnPagedVenuesWhenQueryProvided() {
    // Given
    VenueListQuery query = new VenueListQuery(2, 10, "Nature");
    VenueSummaryReadModel readModel =
        new VenueSummaryReadModel(
            1001L,
            "Nature",
            "0028-0836",
            "0410462",
            "OPENALEX",
            "US",
            Instant.parse("2026-02-12T10:00:00Z"));
    PageResult<VenueSummaryReadModel> serviceResult = PageResult.of(List.of(readModel), 2, 10, 31);
    VenueItemResponse itemResponse =
        new VenueItemResponse(
            1001L,
            "Nature",
            "0028-0836",
            "0410462",
            "OPENALEX",
            "US",
            Instant.parse("2026-02-12T10:00:00Z"));

    when(venueApiConverter.toQuery(any(VenueListRequest.class))).thenReturn(query);
    when(venueQueryService.listVenues(query)).thenReturn(serviceResult);
    when(venueApiConverter.toItemResponse(readModel)).thenReturn(itemResponse);

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
        .jsonPath("$.items[0].displayName")
        .isEqualTo("Nature");

    verify(venueQueryService).listVenues(query);
    verify(venueApiConverter).toItemResponse(readModel);
  }

  /// 未指定查询参数时应使用默认分页参数。
  @Test
  @DisplayName("GET /venues 无参数时 null 应透传到服务层归一化")
  void shouldUseDefaultParamsWhenQueryNotProvided() {
    // Given
    VenueListQuery query = new VenueListQuery(null, null, null);
    PageResult<VenueSummaryReadModel> serviceResult = PageResult.empty(1, 20);

    when(venueApiConverter.toQuery(any(VenueListRequest.class))).thenReturn(query);
    when(venueQueryService.listVenues(query)).thenReturn(serviceResult);

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

    // Then
    ArgumentCaptor<VenueListRequest> requestCaptor =
        ArgumentCaptor.forClass(VenueListRequest.class);
    verify(venueApiConverter).toQuery(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).isEqualTo(new VenueListRequest(null, null, null));
  }

  /// 非法分页参数不应在控制器层被拦截，需继续传递到 QueryService 做归一化。
  @Test
  @DisplayName("GET /venues 非法分页参数应继续委托到服务层")
  void shouldDelegateWhenInvalidPagingParamsProvided() {
    // Given
    VenueListQuery query = new VenueListQuery(0, 1000, "Nature");
    PageResult<VenueSummaryReadModel> serviceResult = PageResult.empty(1, 100);

    when(venueApiConverter.toQuery(any(VenueListRequest.class))).thenReturn(query);
    when(venueQueryService.listVenues(query)).thenReturn(serviceResult);

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

    // Then
    ArgumentCaptor<VenueListRequest> requestCaptor =
        ArgumentCaptor.forClass(VenueListRequest.class);
    verify(venueApiConverter).toQuery(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).isEqualTo(new VenueListRequest(0, 1000, "Nature"));
    verify(venueQueryService).listVenues(query);
  }
}
