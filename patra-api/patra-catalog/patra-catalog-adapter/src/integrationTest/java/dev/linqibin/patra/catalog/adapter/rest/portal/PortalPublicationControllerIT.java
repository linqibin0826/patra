package dev.linqibin.patra.catalog.adapter.rest.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.linqibin.patra.catalog.app.usecase.portal.query.PortalFeedQueryService;
import dev.linqibin.patra.catalog.app.usecase.portal.query.PortalPublicationDetailQueryService;
import dev.linqibin.patra.catalog.app.usecase.portal.query.dto.PortalFeedQuery;
import dev.linqibin.patra.catalog.domain.exception.PublicationNotFoundException;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationDetailReadModel;
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
        .isNumber();

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
}
