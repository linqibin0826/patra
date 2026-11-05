package dev.patra.catalog.adapter.publication.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.app.publication.usecase.query.PublicationNeighborQueryService;
import dev.patra.catalog.domain.publication.model.enums.SubjectNeighborReason;
import dev.patra.catalog.domain.publication.model.read.SubjectNeighborReadModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// `PublicationNeighborEndpointImpl` REST 切片测试：`GET /_internal/publications/latest`、
/// `GET /_internal/publications/{id}/subject-neighbors`。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(controllers = PublicationNeighborEndpointImpl.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import(PublicationNeighborEndpointImpl.class)
@AutoConfigureRestTestClient
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("PublicationNeighborEndpoint REST 切片测试")
class PublicationNeighborEndpointIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final long BIG_ID = 9_007_199_254_740_993L;

  @Autowired private RestTestClient restClient;

  @MockitoBean private PublicationNeighborQueryService neighborService;

  @Test
  @DisplayName("最新收录：按服务顺序输出，id 为字符串")
  void latest_returns_ids_as_strings() {
    when(neighborService.latest(28)).thenReturn(List.of(BIG_ID, 1L));

    JsonNode json = JSON.readTree(get("/_internal/publications/latest?limit=28"));

    assertThat(json.get("ids").get(0).isString()).isTrue();
    assertThat(json.get("ids").get(0).asString()).isEqualTo("9007199254740993");
    assertThat(json.get("ids").get(1).asString()).isEqualTo("1");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidNeighborRequests")
  @DisplayName("最新收录、同主题邻居：参数越界 422，不触达服务")
  void neighbor_endpoints_reject_out_of_range_params(String uri) {
    restClient.get().uri(uri).exchange().expectStatus().isEqualTo(422);

    verifyNoInteractions(neighborService);
  }

  static List<String> invalidNeighborRequests() {
    return List.of(
        "/_internal/publications/latest?limit=0",
        "/_internal/publications/latest?limit=101",
        "/_internal/publications/0/subject-neighbors?limit=6",
        "/_internal/publications/5/subject-neighbors?limit=0",
        "/_internal/publications/5/subject-neighbors?limit=21");
  }

  @Test
  @DisplayName("同主题邻居：来源与标签照实输出，不适用的字段为 null")
  void subject_neighbors_maps_reason_and_labels() {
    when(neighborService.subjectNeighbors(5L, 11))
        .thenReturn(
            List.of(
                SubjectNeighborReadModel.builder()
                    .id(BIG_ID)
                    .reason(SubjectNeighborReason.SAME_MESH)
                    .meshDescriptorUi("D012772")
                    .meshTerm("Shock, Septic")
                    .build(),
                SubjectNeighborReadModel.builder()
                    .id(2L)
                    .reason(SubjectNeighborReason.SAME_VENUE)
                    .venueName("Chest")
                    .build()));

    JsonNode items =
        JSON.readTree(get("/_internal/publications/5/subject-neighbors?limit=11")).get("items");

    JsonNode mesh = items.get(0);
    assertThat(mesh.get("id").asString()).isEqualTo("9007199254740993");
    assertThat(mesh.get("reason").asString()).isEqualTo("SAME_MESH");
    assertThat(mesh.get("meshDescriptorUi").asString()).isEqualTo("D012772");
    assertThat(mesh.get("meshTerm").asString()).isEqualTo("Shock, Septic");
    assertThat(mesh.get("venueName").isNull()).isTrue();
    JsonNode venue = items.get(1);
    assertThat(venue.get("reason").asString()).isEqualTo("SAME_VENUE");
    assertThat(venue.get("venueName").asString()).isEqualTo("Chest");
    assertThat(venue.get("meshTerm").isNull()).isTrue();
    assertThat(venue.get("meshDescriptorUi").isNull()).isTrue();
  }

  private String get(String uri) {
    return restClient
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }
}
