package dev.linqibin.patra.catalog.adapter.rest.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.adapter.config.TestConfiguration;
import dev.linqibin.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import dev.linqibin.patra.catalog.app.usecase.publication.query.dto.PublicationListQuery;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
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

/// PublicationController REST 接口切片测试。
///
/// **测试目标**：
///
/// - 路径与查询参数绑定正确
/// - 控制器委托链路正确（Controller -> Converter -> QueryService）
/// - MapStruct 转换器字段映射正确（使用真实 PublicationApiConverter）
/// - 响应结构与字段序列化正确（Long → String）
@WebMvcTest(controllers = PublicationController.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Import(PublicationController.class)
@AutoConfigureRestTestClient
@DisplayName("PublicationController REST 接口切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PublicationControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private PublicationQueryService publicationQueryService;

  /// 指定查询参数时应返回分页结果。
  @Test
  @DisplayName("GET /publications 指定参数应返回 200 和分页数据")
  void shouldReturnPagedPublicationsWhenQueryProvided() {
    // Given
    PublicationSummaryReadModel readModel =
        new PublicationSummaryReadModel(
            1001L,
            "Cancer treatment study",
            "12345678",
            "10.1234/test",
            2024,
            "en",
            true,
            "gold",
            2001L,
            "Nature",
            42,
            Instant.parse("2026-02-12T10:00:00Z"));
    PageResult<PublicationSummaryReadModel> serviceResult =
        PageResult.of(List.of(readModel), 2, 10, 31);

    when(publicationQueryService.listPublications(any(PublicationListQuery.class)))
        .thenReturn(serviceResult);

    // When & Then
    restClient
        .get()
        .uri("/publications?page=2&pageSize=10&q=cancer&yearFrom=2020&yearTo=2024")
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
        .isEqualTo("Cancer treatment study")
        .jsonPath("$.items[0].pmid")
        .isEqualTo("12345678")
        .jsonPath("$.items[0].doi")
        .isEqualTo("10.1234/test")
        .jsonPath("$.items[0].publicationYear")
        .isEqualTo(2024)
        .jsonPath("$.items[0].languageCode")
        .isEqualTo("en")
        .jsonPath("$.items[0].isOa")
        .isEqualTo(true)
        .jsonPath("$.items[0].oaStatus")
        .isEqualTo("gold")
        .jsonPath("$.items[0].venueId")
        .isEqualTo("2001")
        .jsonPath("$.items[0].venueName")
        .isEqualTo("Nature")
        .jsonPath("$.items[0].citationCount")
        .isEqualTo(42);

    // 验证 QueryService 接收到正确的查询参数（由真实 Converter 转换）
    ArgumentCaptor<PublicationListQuery> queryCaptor =
        ArgumentCaptor.forClass(PublicationListQuery.class);
    verify(publicationQueryService).listPublications(queryCaptor.capture());
    PublicationListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.page()).isEqualTo(2);
    assertThat(capturedQuery.pageSize()).isEqualTo(10);
    assertThat(capturedQuery.q()).isEqualTo("cancer");
    assertThat(capturedQuery.yearFrom()).isEqualTo(2020);
    assertThat(capturedQuery.yearTo()).isEqualTo(2024);
  }

  /// 未指定查询参数时应使用默认分页参数。
  @Test
  @DisplayName("GET /publications 无参数时 null 应透传到服务层归一化")
  void shouldUseDefaultParamsWhenQueryNotProvided() {
    // Given
    PageResult<PublicationSummaryReadModel> serviceResult = PageResult.empty(1, 20);

    when(publicationQueryService.listPublications(any(PublicationListQuery.class)))
        .thenReturn(serviceResult);

    // When
    restClient
        .get()
        .uri("/publications")
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
    ArgumentCaptor<PublicationListQuery> queryCaptor =
        ArgumentCaptor.forClass(PublicationListQuery.class);
    verify(publicationQueryService).listPublications(queryCaptor.capture());
    PublicationListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.page()).isNull();
    assertThat(capturedQuery.pageSize()).isNull();
    assertThat(capturedQuery.q()).isNull();
    assertThat(capturedQuery.yearFrom()).isNull();
    assertThat(capturedQuery.yearTo()).isNull();
    assertThat(capturedQuery.venueId()).isNull();
    assertThat(capturedQuery.isOa()).isNull();
  }

  /// 非法分页参数不应在控制器层被拦截，需继续传递到 QueryService 做归一化。
  @Test
  @DisplayName("GET /publications 非法分页参数应继续委托到服务层")
  void shouldDelegateWhenInvalidPagingParamsProvided() {
    // Given
    PageResult<PublicationSummaryReadModel> serviceResult = PageResult.empty(1, 100);

    when(publicationQueryService.listPublications(any(PublicationListQuery.class)))
        .thenReturn(serviceResult);

    // When
    restClient
        .get()
        .uri("/publications?page=0&pageSize=1000&q=test")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.pageSize")
        .isEqualTo(100);

    // Then — 验证 Converter 忠实地传递了原始非法参数
    ArgumentCaptor<PublicationListQuery> queryCaptor =
        ArgumentCaptor.forClass(PublicationListQuery.class);
    verify(publicationQueryService).listPublications(queryCaptor.capture());
    PublicationListQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.page()).isEqualTo(0);
    assertThat(capturedQuery.pageSize()).isEqualTo(1000);
    assertThat(capturedQuery.q()).isEqualTo("test");
  }
}
