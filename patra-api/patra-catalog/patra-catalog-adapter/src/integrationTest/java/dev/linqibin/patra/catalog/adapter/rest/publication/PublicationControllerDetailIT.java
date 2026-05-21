package dev.linqibin.patra.catalog.adapter.rest.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.adapter.config.TestConfiguration;
import dev.linqibin.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import dev.linqibin.patra.catalog.app.usecase.publication.query.dto.PublicationDetailQuery;
import dev.linqibin.patra.catalog.domain.exception.PublicationNotFoundException;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.AbstractInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.IdentifierInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.KeywordInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo.MeshQualifierInfo;
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

/// PublicationController 详情查询 REST 接口切片测试。
///
/// **测试目标**：
///
/// - 200 OK：返回完整详情（含嵌套子表数据）
/// - 404 Not Found：ID 不存在时 PublicationNotFoundException 被 ErrorResolutionEngine 自动映射
/// - 路径参数绑定正确
/// - MapStruct 转换器字段映射正确（使用真实 PublicationApiConverter）
@WebMvcTest(controllers = PublicationController.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Import(PublicationController.class)
@AutoConfigureRestTestClient
@DisplayName("PublicationController 详情查询 REST 接口切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PublicationControllerDetailIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private PublicationQueryService publicationQueryService;

  /// 查询存在的 ID 应返回 200 和完整详情。
  @Test
  @DisplayName("GET /publications/{id} 存在的 ID 应返回 200 和完整详情")
  void shouldReturn200WithDetailWhenIdExists() {
    // Given
    Long validId = 1001L;
    PublicationDetailReadModel detail =
        PublicationDetailReadModel.builder()
            .id(validId)
            .provenanceCode("PUBMED")
            .title("Cancer treatment study")
            .originalTitle("Cancer treatment study (original)")
            .pmid("12345678")
            .doi("10.1234/test")
            .publicationYear(2024)
            .languageCode("en")
            .languageRaw("eng")
            .languageBase("en")
            .publicationStatus("ppublish")
            .mediaType("print")
            .isOa(true)
            .oaStatus("gold")
            .venueId(2001L)
            .venueName("Nature")
            .citationCount(42)
            .numberOfReferences(30)
            .authorsComplete(true)
            .conflictOfInterest("None declared")
            .lastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .createdAt(Instant.parse("2026-02-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .abstracts(
                List.of(new AbstractInfo("This is the abstract.", null, "Copyright 2024", "MAIN")))
            .identifiers(List.of(new IdentifierInfo("DOI", "10.1234/test", "crossref")))
            .keywords(List.of(new KeywordInfo("apoptosis", true, "MeSH")))
            .meshHeadings(
                List.of(
                    new MeshHeadingInfo(
                        "D001234", true, List.of(new MeshQualifierInfo("Q000235", true)))))
            .build();

    when(publicationQueryService.getPublicationDetail(any(PublicationDetailQuery.class)))
        .thenReturn(detail);

    // When & Then: 使用真实 PublicationApiConverter 自动转换
    restClient
        .get()
        .uri("/publications/{id}", validId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        // 主表字段
        .jsonPath("$.id")
        .isEqualTo("1001")
        .jsonPath("$.provenanceCode")
        .isEqualTo("PUBMED")
        .jsonPath("$.title")
        .isEqualTo("Cancer treatment study")
        .jsonPath("$.originalTitle")
        .isEqualTo("Cancer treatment study (original)")
        .jsonPath("$.pmid")
        .isEqualTo("12345678")
        .jsonPath("$.doi")
        .isEqualTo("10.1234/test")
        .jsonPath("$.publicationYear")
        .isEqualTo(2024)
        .jsonPath("$.languageCode")
        .isEqualTo("en")
        .jsonPath("$.isOa")
        .isEqualTo(true)
        .jsonPath("$.oaStatus")
        .isEqualTo("gold")
        .jsonPath("$.venueId")
        .isEqualTo("2001")
        .jsonPath("$.venueName")
        .isEqualTo("Nature")
        .jsonPath("$.citationCount")
        .isEqualTo(42)
        .jsonPath("$.numberOfReferences")
        .isEqualTo(30)
        // 摘要
        .jsonPath("$.abstracts.length()")
        .isEqualTo(1)
        .jsonPath("$.abstracts[0].plainText")
        .isEqualTo("This is the abstract.")
        .jsonPath("$.abstracts[0].copyright")
        .isEqualTo("Copyright 2024")
        .jsonPath("$.abstracts[0].abstractType")
        .isEqualTo("MAIN")
        // 标识符
        .jsonPath("$.identifiers.length()")
        .isEqualTo(1)
        .jsonPath("$.identifiers[0].type")
        .isEqualTo("DOI")
        .jsonPath("$.identifiers[0].value")
        .isEqualTo("10.1234/test")
        .jsonPath("$.identifiers[0].source")
        .isEqualTo("crossref")
        // 关键词
        .jsonPath("$.keywords.length()")
        .isEqualTo(1)
        .jsonPath("$.keywords[0].term")
        .isEqualTo("apoptosis")
        .jsonPath("$.keywords[0].major")
        .isEqualTo(true)
        .jsonPath("$.keywords[0].keywordSet")
        .isEqualTo("MeSH")
        // MeSH 标引
        .jsonPath("$.meshHeadings.length()")
        .isEqualTo(1)
        .jsonPath("$.meshHeadings[0].descriptorUi")
        .isEqualTo("D001234")
        .jsonPath("$.meshHeadings[0].majorTopic")
        .isEqualTo(true)
        .jsonPath("$.meshHeadings[0].qualifiers.length()")
        .isEqualTo(1)
        .jsonPath("$.meshHeadings[0].qualifiers[0].qualifierUi")
        .isEqualTo("Q000235")
        .jsonPath("$.meshHeadings[0].qualifiers[0].majorTopic")
        .isEqualTo(true);

    // 验证 QueryService 接收到正确的查询参数
    ArgumentCaptor<PublicationDetailQuery> queryCaptor =
        ArgumentCaptor.forClass(PublicationDetailQuery.class);
    verify(publicationQueryService).getPublicationDetail(queryCaptor.capture());
    assertThat(queryCaptor.getValue().id()).isEqualTo(validId);
  }

  /// 查询不存在的 ID 应返回 404 Not Found。
  ///
  /// PublicationNotFoundException 携带 StandardErrorTrait.NOT_FOUND，
  /// 由 DefaultErrorResolutionEngine 内置映射自动转换为 HTTP 404。
  @Test
  @DisplayName("GET /publications/{id} 不存在的 ID 应返回 404 Not Found")
  void shouldReturn404WhenIdNotExists() {
    // Given
    Long invalidId = 999999L;
    when(publicationQueryService.getPublicationDetail(any(PublicationDetailQuery.class)))
        .thenThrow(new PublicationNotFoundException(invalidId));

    // When & Then
    restClient.get().uri("/publications/{id}", invalidId).exchange().expectStatus().isNotFound();

    // 验证路径参数正确绑定
    ArgumentCaptor<PublicationDetailQuery> queryCaptor =
        ArgumentCaptor.forClass(PublicationDetailQuery.class);
    verify(publicationQueryService).getPublicationDetail(queryCaptor.capture());
    assertThat(queryCaptor.getValue().id()).isEqualTo(invalidId);
  }
}
