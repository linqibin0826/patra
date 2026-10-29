package dev.patra.catalog.adapter.rest.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.app.usecase.portal.query.PortalFeedQueryService;
import dev.patra.catalog.app.usecase.portal.query.PortalPublicationCardQueryService;
import dev.patra.catalog.app.usecase.portal.query.PortalPublicationDetailQueryService;
import dev.patra.catalog.app.usecase.portal.query.PortalPublicationSearchQueryService;
import dev.patra.catalog.app.usecase.portal.query.dto.PortalFeedQuery;
import dev.patra.catalog.domain.exception.PublicationNotFoundException;
import dev.patra.catalog.domain.model.read.portal.FacetCount;
import dev.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.patra.catalog.domain.model.read.portal.PublicationDetailReadModel;
import dev.patra.catalog.domain.model.read.portal.PublicationSearchFacets;
import dev.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.patra.catalog.domain.model.read.portal.PublicationSearchSort;
import dev.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import dev.patra.catalog.domain.query.PageResult;
import dev.patra.catalog.domain.query.PagingParams;
import java.time.Instant;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@WebMvcTest(controllers = PortalPublicationController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import({PortalPublicationController.class, PortalApiConverter.class})
@AutoConfigureRestTestClient
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("PortalPublicationController REST 切片测试")
class PortalPublicationControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private PortalFeedQueryService portalFeedQueryService;
  @MockitoBean private PortalPublicationDetailQueryService portalPublicationDetailQueryService;
  @MockitoBean private PortalPublicationSearchQueryService portalPublicationSearchQueryService;
  @MockitoBean private PortalPublicationCardQueryService portalPublicationCardQueryService;

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
        .doesNotExist()
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
        .doesNotExist()
        .jsonPath("$.estimatedReadMin")
        .isEmpty()
        .jsonPath("$.retracted")
        .isEqualTo(false)
        .jsonPath("$.hasFullText")
        .isEqualTo(false)
        .jsonPath("$.hasPdf")
        .isEqualTo(false)
        .jsonPath("$.pdfSizeBytes")
        .isEmpty()
        .jsonPath("$.pmcid")
        .isEqualTo("PMC123456");
  }

  @Test
  @DisplayName("可读全文时给出阅读时长 ceil(字数 / 230)、可读与 PDF 标志和 PDF 大小")
  void should_return_estimated_read_min_when_full_text_readable() {
    PublicationDetailReadModel model =
        PublicationDetailReadModel.builder()
            .id(1L)
            .title("T")
            .evidenceLevel(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL)
            .provenanceCode("PUBMED")
            .hasFullText(true)
            .hasPdf(true)
            .pdfSizeBytes(2_621_440L)
            .wordCount(5230)
            .build();
    when(portalPublicationDetailQueryService.getById(1L)).thenReturn(model);

    restClient
        .get()
        .uri("/portal/publications/1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.estimatedReadMin")
        .isEqualTo(23)
        .jsonPath("$.hasFullText")
        .isEqualTo(true)
        .jsonPath("$.hasPdf")
        .isEqualTo(true)
        .jsonPath("$.pdfSizeBytes")
        .isEqualTo("2621440");
  }

  @Test
  @DisplayName("撤稿文献的详情带 retracted = true")
  void should_return_retracted_flag_when_retracted() {
    PublicationDetailReadModel model =
        PublicationDetailReadModel.builder()
            .id(2L)
            .title("T")
            .evidenceLevel(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL)
            .provenanceCode("PUBMED")
            .retracted(true)
            .build();
    when(portalPublicationDetailQueryService.getById(2L)).thenReturn(model);

    restClient
        .get()
        .uri("/portal/publications/2")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.retracted")
        .isEqualTo(true);
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

  // ===== GET /portal/publications/batch =====

  @Test
  @DisplayName("batch：按服务顺序返回 items，超 2^53 的 ID 为字符串，没有 bookmarks")
  void should_return_cards_in_service_order() {
    long bigId = 9_007_199_254_740_993L;
    when(portalPublicationCardQueryService.findByIds(List.of(bigId, 2L)))
        .thenReturn(List.of(batchCard(bigId), batchCard(2L)));

    restClient
        .get()
        .uri("/portal/publications/batch?id=" + bigId + "&id=2")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items.length()")
        .isEqualTo(2)
        .jsonPath("$.items[0].id")
        .isEqualTo("9007199254740993")
        .jsonPath("$.items[1].id")
        .isEqualTo("2")
        .jsonPath("$.items[0].bookmarks")
        .doesNotExist();
    verify(portalPublicationCardQueryService).findByIds(List.of(bigId, 2L));
  }

  @Test
  @DisplayName("batch：重复参数绑定为多个 ID，空值被丢弃")
  void should_bind_repeated_id_params_and_drop_blank_ones() {
    when(portalPublicationCardQueryService.findByIds(List.of(5L, 6L))).thenReturn(List.of());

    restClient
        .get()
        .uri("/portal/publications/batch?id=5&id=&id=6")
        .exchange()
        .expectStatus()
        .isOk();

    verify(portalPublicationCardQueryService).findByIds(List.of(5L, 6L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidBatchQueries")
  @DisplayName("batch：缺失、只有空值、非正整数、超过 100 个 → 422，不触达服务")
  void should_reject_invalid_ids_with_422_without_calling_service(String query) {
    restClient
        .get()
        .uri("/portal/publications/batch" + query)
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    verifyNoInteractions(portalPublicationCardQueryService);
  }

  static List<String> invalidBatchQueries() {
    String ids101 =
        LongStream.rangeClosed(1, 101)
            .mapToObj(i -> "id=" + i)
            .collect(Collectors.joining("&", "?", ""));
    return List.of("", "?id=", "?id=0", ids101);
  }

  @Test
  @DisplayName("batch：逗号连在一个值里不拆开，也不静默放过")
  void should_reject_comma_joined_ids() {
    restClient
        .get()
        .uri("/portal/publications/batch?id=1,2")
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    verifyNoInteractions(portalPublicationCardQueryService);
  }

  @Test
  @DisplayName("batch 是字面路径，不会落到 /{id} 详情端点")
  void should_not_route_batch_to_detail_endpoint() {
    when(portalPublicationCardQueryService.findByIds(List.of(1L))).thenReturn(List.of());

    restClient.get().uri("/portal/publications/batch?id=1").exchange().expectStatus().isOk();

    verifyNoInteractions(portalPublicationDetailQueryService);
  }

  private static PortalPaperReadModel batchCard(long id) {
    return PortalPaperReadModel.builder()
        .id(id)
        .title("Card " + id)
        .provenanceCode("PUBMED")
        .evidenceLevel(EvidenceLevel.UNKNOWN)
        .build();
  }

  @Test
  @DisplayName("只有年份的文献：publishedDate 与 dates 只输出年份，不补月日；卡片同样")
  void year_only_publication_has_no_fabricated_month_day() {
    PublicationDetailReadModel model =
        PublicationDetailReadModel.builder()
            .id(11L)
            .title("Year only")
            .publicationYear(2023)
            .publishedDate("2023")
            .evidenceLevel(EvidenceLevel.UNKNOWN)
            .provenanceCode("PUBMED")
            .dates(
                List.of(
                    PublicationDetailReadModel.PublicationDateView.of("published", "2023"),
                    PublicationDetailReadModel.PublicationDateView.of("pmcrelease", "2022-05")))
            .build();
    when(portalPublicationDetailQueryService.getById(11L)).thenReturn(model);
    when(portalPublicationCardQueryService.findByIds(List.of(11L)))
        .thenReturn(
            List.of(
                PortalPaperReadModel.builder()
                    .id(11L)
                    .title("Year only")
                    .publicationYear(2023)
                    .publishedDate("2023")
                    .provenanceCode("PUBMED")
                    .build()));

    restClient
        .get()
        .uri("/portal/publications/11")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.publishedDate")
        .isEqualTo("2023")
        .jsonPath("$.dates[0].date")
        .isEqualTo("2023")
        .jsonPath("$.dates[1].date")
        .isEqualTo("2022-05");
    restClient
        .get()
        .uri("/portal/publications/batch?id=11")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items[0].publishedDate")
        .isEqualTo("2023");
  }
}
