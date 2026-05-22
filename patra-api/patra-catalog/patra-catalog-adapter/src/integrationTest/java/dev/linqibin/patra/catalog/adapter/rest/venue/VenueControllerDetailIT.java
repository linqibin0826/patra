package dev.linqibin.patra.catalog.adapter.rest.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.adapter.config.TestConfiguration;
import dev.linqibin.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import dev.linqibin.patra.catalog.app.usecase.venue.query.VenueQueryService;
import dev.linqibin.patra.catalog.app.usecase.venue.query.dto.VenueDetailQuery;
import dev.linqibin.patra.catalog.domain.exception.VenueNotFoundException;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
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

/// VenueController 详情查询 REST 接口切片测试。
///
/// **测试目标**：
///
/// - 200 OK：返回完整详情
/// - 404 Not Found：ID 不存在时 VenueNotFoundException 被 ErrorResolutionEngine 自动映射
/// - 路径参数绑定正确
/// - MapStruct 转换器字段映射正确（使用真实 VenueApiConverter）
@WebMvcTest(controllers = VenueController.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Import(VenueController.class)
@AutoConfigureRestTestClient
@DisplayName("VenueController 详情查询 REST 接口切片测试")
class VenueControllerDetailIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueQueryService venueQueryService;
  @MockitoBean private PublicationQueryService publicationQueryService;

  /// 查询存在的 ID 应返回 200 和完整详情。
  @Test
  @DisplayName("GET /venues/{id} 存在的 ID 应返回 200 和完整详情")
  void shouldReturn200WithDetailWhenIdExists() {
    // Given
    Long validId = 1001L;
    VenueDetailReadModel detail =
        VenueDetailReadModel.builder()
            .id(validId)
            .venueType("JOURNAL")
            .title("Nature")
            .issnL("0028-0836")
            .nlmId("0410462")
            .openalexId("S12345")
            .abbreviatedTitle("Nature")
            .primaryLanguage("eng")
            .countryCode("US")
            .affiliatedSocieties(List.of())
            .lastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .createdAt(Instant.parse("2026-02-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .build();

    when(venueQueryService.getVenueDetail(any(VenueDetailQuery.class))).thenReturn(detail);

    // When & Then: 使用真实 VenueApiConverter 自动转换
    restClient
        .get()
        .uri("/venues/{id}", validId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo("1001")
        .jsonPath("$.venueType")
        .isEqualTo("JOURNAL")
        .jsonPath("$.title")
        .isEqualTo("Nature")
        .jsonPath("$.issnL")
        .isEqualTo("0028-0836")
        .jsonPath("$.nlmId")
        .isEqualTo("0410462")
        .jsonPath("$.openalexId")
        .isEqualTo("S12345")
        .jsonPath("$.abbreviatedTitle")
        .isEqualTo("Nature")
        .jsonPath("$.primaryLanguage")
        .isEqualTo("eng")
        .jsonPath("$.countryCode")
        .isEqualTo("US");

    // 验证 QueryService 接收到正确的查询参数（路径参数正确绑定到 Query 对象）
    ArgumentCaptor<VenueDetailQuery> queryCaptor = ArgumentCaptor.forClass(VenueDetailQuery.class);
    verify(venueQueryService).getVenueDetail(queryCaptor.capture());
    assertThat(queryCaptor.getValue().id()).isEqualTo(validId);
  }

  /// 查询不存在的 ID 应返回 404 Not Found。
  ///
  /// VenueNotFoundException 携带 StandardErrorTrait.NOT_FOUND，
  /// 由 DefaultErrorResolutionEngine 内置映射自动转换为 HTTP 404。
  @Test
  @DisplayName("GET /venues/{id} 不存在的 ID 应返回 404 Not Found")
  void shouldReturn404WhenIdNotExists() {
    // Given
    Long invalidId = 999999L;
    when(venueQueryService.getVenueDetail(any(VenueDetailQuery.class)))
        .thenThrow(new VenueNotFoundException(invalidId));

    // When & Then
    restClient.get().uri("/venues/{id}", invalidId).exchange().expectStatus().isNotFound();

    // 验证路径参数正确绑定
    ArgumentCaptor<VenueDetailQuery> queryCaptor = ArgumentCaptor.forClass(VenueDetailQuery.class);
    verify(venueQueryService).getVenueDetail(queryCaptor.capture());
    assertThat(queryCaptor.getValue().id()).isEqualTo(invalidId);
  }
}
