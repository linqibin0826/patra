package dev.patra.catalog.adapter.mesh.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.adapter.mesh.rest.converter.PortalMeshApiConverter;
import dev.patra.catalog.app.mesh.usecase.query.MeshDescriptorQueryService;
import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSuggestion;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// `PortalMeshController` REST 切片测试：响应结构、参数归一化与校验。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(controllers = PortalMeshController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import({PortalMeshController.class, PortalMeshApiConverter.class})
@AutoConfigureRestTestClient
@DisplayName("PortalMeshController REST 切片测试")
class PortalMeshControllerIT {

  private static final String SUGGEST = "/portal/mesh/suggest";

  @Autowired private RestTestClient restClient;

  @MockitoBean private MeshDescriptorQueryService service;

  @Test
  @DisplayName("GET /portal/mesh/suggest 返回 items 信封，入口词命中带 matchedTerm")
  void should_return_items_with_matched_term_and_tree_numbers() {
    when(service.suggest(any()))
        .thenReturn(
            List.of(
                MeshDescriptorSuggestion.of(
                    MeshDescriptorSummary.of("D009369", "Neoplasms", List.of("C04")), "Cancer"),
                MeshDescriptorSuggestion.of(
                    MeshDescriptorSummary.of(
                        "D000072716", "Cancer Pain", List.of("C23.888.592.612.212")),
                    null)));

    restClient
        .get()
        .uri(SUGGEST + "?q=Cancer")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.items[0].ui")
        .isEqualTo("D009369")
        .jsonPath("$.items[0].name")
        .isEqualTo("Neoplasms")
        .jsonPath("$.items[0].matchedTerm")
        .isEqualTo("Cancer")
        .jsonPath("$.items[0].treeNumbers[0]")
        .isEqualTo("C04")
        .jsonPath("$.items[1].matchedTerm")
        .doesNotExist();

    verify(service).suggest(SuggestQuery.of("Cancer", 10));
  }

  @Test
  @DisplayName("q 归一化后传给服务")
  void should_pass_normalized_query() {
    when(service.suggest(any())).thenReturn(List.of());

    restClient
        .get()
        .uri(b -> b.path(SUGGEST).queryParam("q", " diabetes   mellitus ").build())
        .exchange()
        .expectStatus()
        .isOk();

    verify(service).suggest(SuggestQuery.of("diabetes mellitus", 10));
  }

  @Test
  @DisplayName("q 缺失 / 1 字、limit 21 都是 422，不触达服务")
  void should_reject_invalid_parameters_with_422() {
    for (String uri : List.of(SUGGEST, SUGGEST + "?q=a", SUGGEST + "?q=Cancer&limit=21")) {
      restClient.get().uri(uri).exchange().expectStatus().isEqualTo(422);
    }

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("limit 不是数字时 4xx，不触达服务")
  void should_reject_non_numeric_limit_without_touching_service() {
    restClient
        .get()
        .uri(SUGGEST + "?q=Cancer&limit=abc")
        .exchange()
        .expectStatus()
        .is4xxClientError();

    verifyNoInteractions(service);
  }
}
