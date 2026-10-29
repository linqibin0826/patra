package dev.patra.catalog.adapter.fulltext.rest;

import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.adapter.fulltext.rest.converter.PortalFullTextApiConverter;
import dev.patra.catalog.app.fulltext.usecase.query.PortalPublicationFullTextQueryService;
import dev.patra.catalog.domain.fulltext.exception.FullTextNotAvailableException;
import dev.patra.catalog.domain.fulltext.model.read.FullTextReadModel;
import dev.patra.catalog.domain.fulltext.model.vo.body.Block;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import dev.patra.catalog.domain.fulltext.model.vo.body.Section;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@WebMvcTest(controllers = PortalFullTextController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import({PortalFullTextController.class, PortalFullTextApiConverter.class})
@AutoConfigureRestTestClient
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("PortalFullTextController REST 切片测试")
class PortalFullTextControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private PortalPublicationFullTextQueryService portalPublicationFullTextQueryService;

  @Test
  @DisplayName("GET /{id}/fulltext 返回署名字段与正文")
  void should_return_full_text() {
    FullTextBody body =
        FullTextBody.builder()
            .schemaVersion(1)
            .sections(
                List.of(
                    Section.of(
                        "s1",
                        List.of(new Inline.Text("Intro")),
                        List.of(),
                        List.of(new Block.Paragraph(List.of(new Inline.Text("x")))))))
            .wordCount(5230)
            .build();
    when(portalPublicationFullTextQueryService.getForPortal(319041872872550658L))
        .thenReturn(
            FullTextReadModel.builder()
                .publicationId(319041872872550658L)
                .pmcid("PMC13138198")
                .licenseCode("CC BY-NC-ND")
                .fetchedAt(Instant.parse("2026-10-20T05:12:00Z"))
                .wordCount(5230)
                .manuscript(false)
                .body(body)
                .build());

    restClient
        .get()
        .uri("/portal/publications/319041872872550658/fulltext")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.publicationId")
        .isEqualTo("319041872872550658")
        .jsonPath("$.pmcid")
        .isEqualTo("PMC13138198")
        .jsonPath("$.pmcUrl")
        .isEqualTo("https://pmc.ncbi.nlm.nih.gov/articles/PMC13138198/")
        .jsonPath("$.license.code")
        .isEqualTo("CC BY-NC-ND")
        .jsonPath("$.license.url")
        .isEqualTo("https://creativecommons.org/licenses/by-nc-nd/4.0/")
        .jsonPath("$.source")
        .isEqualTo("数据来源：美国国家医学图书馆（NLM）PubMed Central")
        .jsonPath("$.fetchedAt")
        .isEqualTo("2026-10-20T05:12:00Z")
        .jsonPath("$.wordCount")
        .isEqualTo(5230)
        .jsonPath("$.estimatedReadMin")
        .isEqualTo(23)
        .jsonPath("$.isManuscript")
        .isEqualTo(false)
        .jsonPath("$.body.sections[0].blocks[0].kind")
        .isEqualTo("paragraph");
  }

  @Test
  @DisplayName("只有 PDF 时返回 200、正文与阅读时长为空，署名照常")
  void should_return_attribution_without_body_when_only_pdf() {
    when(portalPublicationFullTextQueryService.getForPortal(2L))
        .thenReturn(
            FullTextReadModel.builder()
                .publicationId(2L)
                .pmcid("PMC7121018")
                .licenseCode(null)
                .fetchedAt(Instant.parse("2026-10-20T05:12:00Z"))
                .manuscript(false)
                .build());

    restClient
        .get()
        .uri("/portal/publications/2/fulltext")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.body")
        .isEmpty()
        .jsonPath("$.estimatedReadMin")
        .isEmpty()
        .jsonPath("$.license")
        .isEmpty()
        .jsonPath("$.source")
        .isEqualTo("数据来源：美国国家医学图书馆（NLM）PubMed Central");
  }

  @Test
  @DisplayName("不可读也不能下载 PDF 时返回 404 + CATALOG-0404")
  void should_return_404_when_full_text_not_available() {
    when(portalPublicationFullTextQueryService.getForPortal(3L))
        .thenThrow(new FullTextNotAvailableException(3L));

    restClient
        .get()
        .uri("/portal/publications/3/fulltext")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CATALOG-0404");
  }
}
