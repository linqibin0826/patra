package dev.linqibin.patra.catalog.adapter.rest.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.linqibin.patra.catalog.app.usecase.portal.query.PortalFeedQueryService;
import dev.linqibin.patra.catalog.app.usecase.portal.query.PortalPublicationDetailQueryService;
import dev.linqibin.patra.catalog.app.usecase.portal.query.PortalPublicationSearchQueryService;
import dev.linqibin.patra.catalog.app.usecase.portal.query.dto.PortalFeedQuery;
import dev.linqibin.patra.catalog.domain.exception.PublicationNotFoundException;
import dev.linqibin.patra.catalog.domain.model.read.portal.FacetCount;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationDetailReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchSort;
import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@WebMvcTest(controllers = PortalPublicationController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import({PortalPublicationController.class, PortalApiConverter.class})
@AutoConfigureRestTestClient
@TestPropertySource(properties = "linqibin.starter.core.error.context-prefix=CATALOG")
@DisplayName("PortalPublicationController REST 切片测试")
class PortalPublicationControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private PortalFeedQueryService portalFeedQueryService;
  @MockitoBean private PortalPublicationDetailQueryService portalPublicationDetailQueryService;
  @MockitoBean private PortalPublicationSearchQueryService portalPublicationSearchQueryService;

  @Test
  @DisplayName("GET /portal/publications 返回 200 + 对齐前端的分页信封")
  void shouldReturnFeedEnvelope() {
    PortalPaperReadModel model =
        PortalPaperReadModel.builder()
            .id(100428830191L)
            .title("Semaglutide 长期心血管转归")
            .venueName("N Engl J Med")
            .publicationYear(2026)
            .authors(List.of("Perkovic V.", "Tuttle K. R."))
            .citationCount(142)
            .doi("10.1056/NEJMoa2603120")
            .pmid("39812044")
            .provenanceCode("PUBMED")
            .studyType("Journal Article")
            .lastSyncedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .venueId(8841L)
            .evidenceLevel(EvidenceLevel.SYSTEMATIC_REVIEW)
            .abstractSnippet("BACKGROUND: ...")
            .build();
    when(portalFeedQueryService.listFeed(any(PortalFeedQuery.class)))
        .thenReturn(PageResult.of(List.of(model), 1, 14, 137));

    restClient
        .get()
        .uri("/portal/publications?tab=recent&page=1&pageSize=14")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.page")
        .isEqualTo(1)
        .jsonPath("$.pageSize")
        .isEqualTo(14)
        .jsonPath("$.total")
        .isEqualTo(137)
        .jsonPath("$.totalPages")
        .isEqualTo(10)
        .jsonPath("$.items[0].id")
        .isEqualTo("100428830191")
        .jsonPath("$.items[0].journal")
        .isEqualTo("N Engl J Med")
        .jsonPath("$.items[0].authors.length()")
        .isEqualTo(2)
        .jsonPath("$.items[0].cites")
        .isEqualTo(142)
        .jsonPath("$.items[0].bookmarks")
        .isEqualTo(0)
        .jsonPath("$.items[0].source")
        .isEqualTo("PubMed")
        .jsonPath("$.items[0].kind")
        .isEqualTo("Journal Article")
        .jsonPath("$.items[0].aiSummary")
        .isEmpty()
        .jsonPath("$.items[0].estimatedReadMin")
        .isEmpty()
        .jsonPath("$.items[0].minutesAgo")
        .isNumber()
        .jsonPath("$.items[0].venueId")
        .isEqualTo("8841")
        .jsonPath("$.items[0].evidenceLevel.level")
        .isEqualTo("SYSTEMATIC_REVIEW")
        .jsonPath("$.items[0].evidenceLevel.rank")
        .isEqualTo(5)
        .jsonPath("$.items[0].evidenceLevel.derived")
        .isEqualTo(true)
        .jsonPath("$.items[0].abstractSnippet")
        .isEqualTo("BACKGROUND: ...");

    ArgumentCaptor<PortalFeedQuery> captor = ArgumentCaptor.forClass(PortalFeedQuery.class);
    verify(portalFeedQueryService).listFeed(captor.capture());
    assertThat(captor.getValue().tab()).isEqualTo("recent");
    assertThat(captor.getValue().page()).isEqualTo(1);
  }

  @Test
  @DisplayName("非法 tab 在适配器校验层收敛为 422，不触达应用层")
  void shouldRejectInvalidTab() {
    restClient
        .get()
        .uri("/portal/publications?tab=hottest")
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    verifyNoInteractions(portalFeedQueryService);
  }

  @Test
  @DisplayName("pageSize 超过上限 50 在适配器校验层收敛为 422")
  void shouldRejectOversizedPageSize() {
    restClient
        .get()
        .uri("/portal/publications?pageSize=51")
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    verifyNoInteractions(portalFeedQueryService);
  }

  @Test
  @DisplayName("GET /portal/publications/{id} 返回 200 + 对齐前端的文献详情")
  void shouldReturnPublicationDetail() {
    PublicationDetailReadModel model =
        PublicationDetailReadModel.builder()
            .id(319041872872550658L)
            .title("Efficacy of Semaglutide in Type 2 Diabetes")
            .publicationYear(2024)
            .evidenceLevel(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL)
            .doi("10.1056/NEJMoa2401234")
            .pmid("38012044")
            .pmcid("PMC123456")
            .isOa(true)
            .provenanceCode("PUBMED")
            .fullTextUrl("https://x/full")
            .abstractSections(
                List.of(
                    PublicationDetailReadModel.AbstractSectionView.of(
                        "BACKGROUND", "Background text.")))
            .build();
    when(portalPublicationDetailQueryService.getById(319041872872550658L)).thenReturn(model);

    restClient
        .get()
        .uri("/portal/publications/319041872872550658")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo("319041872872550658")
        .jsonPath("$.title")
        .isEqualTo("Efficacy of Semaglutide in Type 2 Diabetes")
        .jsonPath("$.evidenceLevel.derived")
        .isEqualTo(true)
        .jsonPath("$.evidenceLevel.level")
        .isEqualTo("RANDOMIZED_CONTROLLED_TRIAL")
        .jsonPath("$.abstractSections[0].label")
        .isEqualTo("BACKGROUND")
        .jsonPath("$.isOa")
        .isEqualTo(true)
        .jsonPath("$.aiSummary")
        .isEmpty()
        .jsonPath("$.source")
        .isEqualTo("PubMed")
        .jsonPath("$.fullTextUrl")
        .isEqualTo("https://x/full")
        .jsonPath("$.bookmarks")
        .isEqualTo(0)
        .jsonPath("$.estimatedReadMin")
        .isEmpty()
        .jsonPath("$.pmcid")
        .isEqualTo("PMC123456");
  }

  @Test
  @DisplayName("id=0 时路径参数校验失败返回 422，不触达应用层")
  void shouldRejectNonPositivePublicationId() {
    restClient.get().uri("/portal/publications/0").exchange().expectStatus().isEqualTo(422);

    verifyNoInteractions(portalPublicationDetailQueryService);
  }

  @Test
  @DisplayName("文献不存在时返回 404 + CATALOG-0404 错误码")
  void shouldReturn404WhenPublicationNotFound() {
    when(portalPublicationDetailQueryService.getById(999L))
        .thenThrow(new PublicationNotFoundException(999L));

    restClient
        .get()
        .uri("/portal/publications/999")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CATALOG-0404");
  }

  @Test
  @DisplayName("GET /portal/publications/search 返回分页信封，query 映射为 filter，重复参数不拆逗号")
  void shouldSearchAndMapFilter() {
    PortalPaperReadModel model =
        PortalPaperReadModel.builder()
            .id(7L)
            .title("t")
            .venueId(8841L)
            .evidenceLevel(EvidenceLevel.CASE_REPORT)
            .provenanceCode("PUBMED")
            .abstractSnippet("snip")
            .build();
    when(portalPublicationSearchQueryService.search(any(), any()))
        .thenReturn(PageResult.of(List.of(model), 2, 10, 11));

    // 用 uriBuilder 传未编码值：`.uri(String)` 会把已编码的 %20 二次编码成 %2520
    restClient
        .get()
        .uri(
            b ->
                b.path("/portal/publications/search")
                    .queryParam("q", " GLP-1 ")
                    .queryParam("author", "Tuttle")
                    .queryParam("pmid", "39812044")
                    .queryParam("doi", "https://doi.org/10.1056/X")
                    .queryParam("yearFrom", 2020)
                    .queryParam("yearTo", 2026)
                    .queryParam("venue", 8841, 1284)
                    .queryParam("type", "Clinical Trial, Phase III")
                    .queryParam("evidence", "case_report", "UNKNOWN")
                    .queryParam("oa", true)
                    .queryParam("lang", "en", "zh")
                    .queryParam("sort", "year")
                    .queryParam("page", 2)
                    .queryParam("pageSize", 10)
                    .build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.page")
        .isEqualTo(2)
        .jsonPath("$.total")
        .isEqualTo(11)
        .jsonPath("$.items[0].id")
        .isEqualTo("7")
        .jsonPath("$.items[0].venueId")
        .isEqualTo("8841")
        .jsonPath("$.items[0].evidenceLevel.level")
        .isEqualTo("CASE_REPORT")
        .jsonPath("$.items[0].abstractSnippet")
        .isEqualTo("snip");

    ArgumentCaptor<PublicationSearchFilter> filter =
        ArgumentCaptor.forClass(PublicationSearchFilter.class);
    ArgumentCaptor<PagingParams> paging = ArgumentCaptor.forClass(PagingParams.class);
    verify(portalPublicationSearchQueryService).search(filter.capture(), paging.capture());
    PublicationSearchFilter f = filter.getValue();
    assertThat(f.keyword()).isEqualTo("GLP-1");
    assertThat(f.author()).isEqualTo("Tuttle");
    assertThat(f.pmid()).isEqualTo("39812044");
    assertThat(f.doi()).isEqualTo("https://doi.org/10.1056/X");
    assertThat(f.yearFrom()).isEqualTo(2020);
    assertThat(f.yearTo()).isEqualTo(2026);
    assertThat(f.venueIds()).containsExactly(8841L, 1284L);
    assertThat(f.types()).containsExactly("Clinical Trial, Phase III");
    assertThat(f.evidenceLevels())
        .containsExactly(EvidenceLevel.CASE_REPORT, EvidenceLevel.UNKNOWN);
    assertThat(f.isOpenAccess()).isTrue();
    assertThat(f.languages()).containsExactly("en", "zh");
    assertThat(f.sort()).isEqualTo(PublicationSearchSort.YEAR);
    assertThat(paging.getValue().page()).isEqualTo(2);
    assertThat(paging.getValue().pageSize()).isEqualTo(10);
  }

  @Test
  @DisplayName("search 无参数：默认 page=1 / pageSize=20 / LATEST，filter 无筛选")
  void shouldSearchWithDefaults() {
    when(portalPublicationSearchQueryService.search(any(), any()))
        .thenReturn(PageResult.empty(1, 20));

    restClient.get().uri("/portal/publications/search").exchange().expectStatus().isOk();

    ArgumentCaptor<PublicationSearchFilter> filter =
        ArgumentCaptor.forClass(PublicationSearchFilter.class);
    ArgumentCaptor<PagingParams> paging = ArgumentCaptor.forClass(PagingParams.class);
    verify(portalPublicationSearchQueryService).search(filter.capture(), paging.capture());
    assertThat(filter.getValue()).isEqualTo(PublicationSearchFilter.builder().build());
    assertThat(paging.getValue()).isEqualTo(PagingParams.of(1, 20));
  }

  @Test
  @DisplayName("GET /portal/publications/search/facets 返回 6 项 evidence 与标量")
  void shouldReturnFacets() {
    PublicationSearchFacets facets =
        PublicationSearchFacets.builder()
            .years(List.of(FacetCount.of("2026", 17520)))
            .types(List.of(FacetCount.of("Journal Article", 17680)))
            .evidence(
                List.of(FacetCount.of("SYSTEMATIC_REVIEW", 346), FacetCount.of("UNKNOWN", 15500)))
            .languages(List.of(FacetCount.of("en", 18488)))
            .openAccess(0)
            .total(18807)
            .lastSyncedAt(Instant.parse("2026-08-30T04:06:00Z"))
            .build();
    when(portalPublicationSearchQueryService.facets(any())).thenReturn(facets);

    restClient
        .get()
        .uri("/portal/publications/search/facets?yearFrom=2025&sort=year&page=3")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.years[0].value")
        .isEqualTo("2026")
        .jsonPath("$.years[0].count")
        .isEqualTo(17520)
        .jsonPath("$.evidence[1].value")
        .isEqualTo("UNKNOWN")
        .jsonPath("$.openAccess")
        .isEqualTo(0)
        .jsonPath("$.total")
        .isEqualTo(18807)
        .jsonPath("$.lastSyncedAt")
        .isEqualTo("2026-08-30T04:06:00Z");

    ArgumentCaptor<PublicationSearchFilter> filter =
        ArgumentCaptor.forClass(PublicationSearchFilter.class);
    verify(portalPublicationSearchQueryService).facets(filter.capture());
    assertThat(filter.getValue().yearFrom()).isEqualTo(2025);
  }

  @Test
  @DisplayName("非法参数在适配器校验层收敛为 422，不触达应用层")
  void shouldRejectInvalidSearchParams() {
    for (String query :
        List.of(
            "pageSize=51",
            "yearFrom=2026&yearTo=2020",
            "sort=cited",
            "evidence=LEVEL_9",
            "pmid=abc",
            "venue=0",
            "q=" + "x".repeat(201))) {
      restClient
          .get()
          .uri("/portal/publications/search?" + query)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }
    verifyNoInteractions(portalPublicationSearchQueryService);
  }

  @Test
  @DisplayName("空的重复参数被忽略：?venue= 与 venue=1&venue= 都是 200，不是 500")
  void shouldIgnoreBlankRepeatedParams() {
    when(portalPublicationSearchQueryService.search(any(), any()))
        .thenReturn(PageResult.empty(1, 20));

    restClient
        .get()
        .uri("/portal/publications/search?venue=&type=")
        .exchange()
        .expectStatus()
        .isOk();
    restClient
        .get()
        .uri("/portal/publications/search?venue=1&venue=")
        .exchange()
        .expectStatus()
        .isOk();

    ArgumentCaptor<PublicationSearchFilter> filter =
        ArgumentCaptor.forClass(PublicationSearchFilter.class);
    verify(portalPublicationSearchQueryService, times(2)).search(filter.capture(), any());
    assertThat(filter.getAllValues().get(0).venueIds()).isEmpty();
    assertThat(filter.getAllValues().get(0).types()).isEmpty();
    assertThat(filter.getAllValues().get(1).venueIds()).containsExactly(1L);
  }

  @Test
  @DisplayName("/search 与 /{id} 路由不冲突：search 是字面路径优先")
  void searchPathWinsOverIdPattern() {
    when(portalPublicationSearchQueryService.search(any(), any()))
        .thenReturn(PageResult.empty(1, 20));
    restClient.get().uri("/portal/publications/search").exchange().expectStatus().isOk();
    verifyNoInteractions(portalPublicationDetailQueryService);
  }
}
