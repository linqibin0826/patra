package dev.patra.catalog.adapter.portal.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.adapter.portal.rest.converter.PortalApiConverter;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueBrowseQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueCardQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalVenueDetailQueryService;
import dev.patra.catalog.domain.portal.model.read.FacetCount;
import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFacets;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFilter;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseReadModel;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseSort;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@WebMvcTest(controllers = PortalVenueController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import({PortalVenueController.class, PortalApiConverter.class})
@AutoConfigureRestTestClient
@DisplayName("PortalVenueController REST 切片测试")
class PortalVenueControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private PortalVenueBrowseQueryService portalVenueBrowseQueryService;

  @MockitoBean private PortalVenueDetailQueryService portalVenueDetailQueryService;

  @MockitoBean private PortalVenueCardQueryService portalVenueCardQueryService;

  @Test
  @DisplayName("GET /portal/venues 返回 200 + 分页信封 + 期刊卡片（回归）")
  void shouldReturnBrowseResult() {
    VenueBrowseReadModel model =
        VenueBrowseReadModel.builder()
            .id(319041872872550658L)
            .name("Annals of oncology")
            .abbr("Ann Oncol")
            .impactFactor(new BigDecimal("65.4"))
            .jcrQuartile("Q1")
            .foundedYear(1990)
            .nlmId("8007974")
            .build();
    when(portalVenueBrowseQueryService.browse(any(), any()))
        .thenReturn(PageResult.of(List.of(model), 1, 12, 1));

    restClient
        .get()
        .uri("/portal/venues?sort=title&page=1&pageSize=12")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.items")
        .isArray()
        .jsonPath("$.total")
        .isEqualTo(1)
        .jsonPath("$.page")
        .isEqualTo(1)
        .jsonPath("$.items[0].id")
        .isEqualTo("319041872872550658")
        .jsonPath("$.items[0].nlmId")
        .isEqualTo("8007974");
  }

  @Test
  @DisplayName("pageSize 超过上限 50 在适配器校验层收敛为 422，不触达应用层")
  void shouldRejectOversizedPageSize() {
    restClient.get().uri("/portal/venues?pageSize=51").exchange().expectStatus().isEqualTo(422);

    verifyNoInteractions(portalVenueBrowseQueryService);
  }

  @Test
  @DisplayName("GET /portal/venues 多值筛选：subject/jcr 解析为 List，oa 解析为 Boolean")
  void shouldParseMultiValueFilters() {
    when(portalVenueBrowseQueryService.browse(any(), any())).thenReturn(PageResult.empty(2, 12));

    ArgumentCaptor<VenueBrowseFilter> filterCaptor =
        ArgumentCaptor.forClass(VenueBrowseFilter.class);

    restClient
        .get()
        .uri("/portal/venues?subject=Medicine,Oncology&jcr=Q1,Q2&oa=true&page=2")
        .exchange()
        .expectStatus()
        .isOk();

    verify(portalVenueBrowseQueryService).browse(filterCaptor.capture(), any());
    VenueBrowseFilter captured = filterCaptor.getValue();
    assertThat(captured.subjects()).containsExactlyInAnyOrder("Medicine", "Oncology");
    assertThat(captured.jcrQuartiles()).containsExactlyInAnyOrder("Q1", "Q2");
    assertThat(captured.isOpenAccess()).isTrue();
  }

  /// `sort` 参数应正确绑定到 `VenueBrowseFilter.sort`（验证 cas_quartile / cited_by）。
  @Test
  @DisplayName("GET /portal/venues sort 参数绑定：cas_quartile / cited_by 正确传入 VenueBrowseFilter")
  void shouldBindSortToFilter() {
    when(portalVenueBrowseQueryService.browse(any(), any())).thenReturn(PageResult.empty(1, 12));

    ArgumentCaptor<VenueBrowseFilter> filterCaptor =
        ArgumentCaptor.forClass(VenueBrowseFilter.class);

    // cas_quartile → VenueBrowseSort.CAS_QUARTILE
    restClient.get().uri("/portal/venues?sort=cas_quartile").exchange().expectStatus().isOk();

    verify(portalVenueBrowseQueryService).browse(filterCaptor.capture(), any());
    assertThat(filterCaptor.getValue().sort()).isEqualTo(VenueBrowseSort.CAS_QUARTILE);

    reset(portalVenueBrowseQueryService);
    when(portalVenueBrowseQueryService.browse(any(), any())).thenReturn(PageResult.empty(1, 12));

    // cited_by → VenueBrowseSort.CITED_BY
    restClient.get().uri("/portal/venues?sort=cited_by").exchange().expectStatus().isOk();

    ArgumentCaptor<VenueBrowseFilter> filterCaptor2 =
        ArgumentCaptor.forClass(VenueBrowseFilter.class);
    verify(portalVenueBrowseQueryService).browse(filterCaptor2.capture(), any());
    assertThat(filterCaptor2.getValue().sort()).isEqualTo(VenueBrowseSort.CITED_BY);
  }

  /// `cas` / `casTop` / `doaj` / `country` 参数应正确绑定到 `VenueBrowseFilter`。
  @Test
  @DisplayName("GET /portal/venues 筛选维度绑定：cas / casTop / doaj / country 正确传入 VenueBrowseFilter")
  void shouldBindAdditionalFacetFilters() {
    when(portalVenueBrowseQueryService.browse(any(), any())).thenReturn(PageResult.empty(1, 12));

    ArgumentCaptor<VenueBrowseFilter> filterCaptor =
        ArgumentCaptor.forClass(VenueBrowseFilter.class);

    restClient
        .get()
        .uri("/portal/venues?cas=Q1,Q2&casTop=true&doaj=false&country=CN,US")
        .exchange()
        .expectStatus()
        .isOk();

    verify(portalVenueBrowseQueryService).browse(filterCaptor.capture(), any());
    VenueBrowseFilter captured = filterCaptor.getValue();
    assertThat(captured.casQuartiles()).containsExactlyInAnyOrder("Q1", "Q2");
    assertThat(captured.casTop()).isTrue();
    assertThat(captured.doaj()).isFalse();
    assertThat(captured.countryCodes()).containsExactlyInAnyOrder("CN", "US");
  }

  @Test
  @DisplayName("GET /portal/venues/facets 返回 200 + 正确 JSON 结构")
  void shouldReturnFacets() {
    VenueBrowseFacets facets =
        VenueBrowseFacets.builder()
            .subjects(List.of(FacetCount.of("Medicine", 120), FacetCount.of("Oncology", 45)))
            .jcrQuartiles(List.of(FacetCount.of("Q1", 80)))
            .casQuartiles(List.of())
            .countries(List.of())
            .casTop(15)
            .openAccess(60)
            .doaj(30)
            .build();
    when(portalVenueBrowseQueryService.facets(any())).thenReturn(facets);

    restClient
        .get()
        .uri("/portal/venues/facets?q=x&jcr=Q1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.subjects[0].value")
        .isEqualTo("Medicine")
        .jsonPath("$.subjects[0].count")
        .isEqualTo(120)
        .jsonPath("$.jcrQuartiles[0].value")
        .isEqualTo("Q1")
        .jsonPath("$.casTop")
        .isEqualTo(15)
        .jsonPath("$.openAccess")
        .isEqualTo(60)
        .jsonPath("$.doaj")
        .isEqualTo(30);
  }

  @Test
  @DisplayName("GET /portal/venues/batch：按服务顺序返回卡片，id 为字符串，带主 NLM（保留前导零）")
  void should_return_batch_cards_in_service_order_with_nlm_id() {
    when(portalVenueCardQueryService.findByIds(List.of(2L, 1L)))
        .thenReturn(
            List.of(
                VenueBrowseReadModel.builder().id(2L).name("Swiss").nlmId("0404401").build(),
                VenueBrowseReadModel.builder().id(1L).name("Lancet").nlmId("2985213R").build()));

    restClient
        .get()
        .uri("/portal/venues/batch?id=2&id=1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items.length()")
        .isEqualTo(2)
        .jsonPath("$.items[0].id")
        .isEqualTo("2")
        .jsonPath("$.items[0].nlmId")
        .isEqualTo("0404401")
        .jsonPath("$.items[1].id")
        .isEqualTo("1")
        .jsonPath("$.items[1].nlmId")
        .isEqualTo("2985213R");

    verify(portalVenueCardQueryService).findByIds(List.of(2L, 1L));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidBatchQueries")
  @DisplayName("GET /portal/venues/batch：缺失、只有空值、非正整数、超过 100 个 → 422，不触达服务")
  void should_reject_invalid_batch_ids_with_422(String query) {
    restClient.get().uri("/portal/venues/batch" + query).exchange().expectStatus().isEqualTo(422);

    verifyNoInteractions(portalVenueCardQueryService);
  }

  /// 非法的批量请求参数。
  ///
  /// @return 查询串
  static List<String> invalidBatchQueries() {
    String ids101 =
        LongStream.rangeClosed(1, 101)
            .mapToObj(i -> "id=" + i)
            .collect(Collectors.joining("&", "?", ""));
    return List.of("", "?id=", "?id=0", ids101);
  }
}
