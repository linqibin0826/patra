package dev.patra.catalog.adapter.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.api.publication.dto.PublicationResolveResp;
import dev.patra.catalog.app.usecase.publication.query.PublicationResolveQueryService;
import dev.patra.catalog.app.usecase.publication.query.dto.PublicationResolution;
import dev.patra.catalog.domain.model.read.publication.PublicationResolveReadModel;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// `PublicationEndpointImpl` REST 切片测试：`POST /_internal/publications/resolve/by-ids|by-pmids`。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(controllers = PublicationEndpointImpl.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import(PublicationEndpointImpl.class)
@AutoConfigureRestTestClient
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("PublicationEndpoint REST 切片测试")
class PublicationEndpointIT {

  /// 与 interaction 侧 `RestClient` 默认一致的映射器：用来验证字符串 ID 能读回 `Long`。
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final long BIG_ID = 9_007_199_254_740_993L;

  @Autowired private RestTestClient restClient;

  @MockitoBean private PublicationResolveQueryService service;

  @Test
  @DisplayName("按 ID：按服务顺序输出，id 为字符串，不存在的行除 key 外全为 null")
  void should_return_rows_in_service_order_with_string_ids() {
    when(service.resolveByIds(List.of(BIG_ID, 1L)))
        .thenReturn(
            List.of(
                PublicationResolution.of(Long.toString(BIG_ID), model(BIG_ID, false)),
                PublicationResolution.of("1", null)));

    JsonNode json = JSON.readTree(post("/by-ids", "{\"ids\":[" + BIG_ID + ",1]}"));

    JsonNode first = json.get("items").get(0);
    assertThat(first.get("key").asString()).isEqualTo("9007199254740993");
    assertThat(first.get("exists").asBoolean()).isTrue();
    assertThat(first.get("state").asString()).isEqualTo("ACTIVE");
    assertThat(first.get("id").isString()).isTrue();
    assertThat(first.get("id").asString()).isEqualTo("9007199254740993");
    assertThat(first.get("pmid").asString()).isEqualTo("38001234");
    assertThat(first.get("title").asString()).isEqualTo("Empagliflozin in HFpEF");
    assertThat(first.get("journal").asString()).isEqualTo("The New England journal of medicine");
    assertThat(first.get("year").asInt()).isEqualTo(2021);
    JsonNode missing = json.get("items").get(1);
    assertThat(missing.get("key").asString()).isEqualTo("1");
    assertThat(missing.get("exists").asBoolean()).isFalse();
    for (String field : List.of("state", "id", "pmid", "title", "journal", "year")) {
      assertThat(missing.get(field).isNull()).as(field).isTrue();
    }
  }

  @Test
  @DisplayName("软删映射为 DELETED，标题等照样有值")
  void should_map_soft_deleted_to_deleted_state() {
    when(service.resolveByIds(List.of(7L)))
        .thenReturn(List.of(PublicationResolution.of("7", model(7L, true))));

    JsonNode first = JSON.readTree(post("/by-ids", "{\"ids\":[7]}")).get("items").get(0);

    assertThat(first.get("state").asString()).isEqualTo("DELETED");
    assertThat(first.get("title").asString()).isEqualTo("Empagliflozin in HFpEF");
  }

  @Test
  @DisplayName("响应体用默认 JsonMapper 读回契约 record，字符串 id 变回 Long")
  void should_round_trip_id_as_long_with_default_json_mapper() {
    when(service.resolveByIds(List.of(BIG_ID)))
        .thenReturn(List.of(PublicationResolution.of(Long.toString(BIG_ID), model(BIG_ID, false))));

    PublicationResolveResp resp =
        JSON.readValue(post("/by-ids", "{\"ids\":[" + BIG_ID + "]}"), PublicationResolveResp.class);

    assertThat(resp.items().get(0).id()).isEqualTo(BIG_ID);
  }

  @Test
  @DisplayName("按 PMID：委托服务")
  void should_resolve_by_pmids() {
    when(service.resolveByPmids(List.of("38001234")))
        .thenReturn(List.of(PublicationResolution.of("38001234", model(5L, false))));

    post("/by-pmids", "{\"pmids\":[\"38001234\"]}");

    verify(service).resolveByPmids(List.of("38001234"));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("invalidRequests")
  @DisplayName("非法请求 422，不触达服务")
  void should_reject_invalid_requests_with_422_without_calling_service(String path, String body) {
    restClient
        .post()
        .uri("/_internal/publications/resolve" + path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    verifyNoInteractions(service);
  }

  static List<Arguments> invalidRequests() {
    String ids101 =
        LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
    return List.of(
        Arguments.of("/by-ids", "{\"ids\":[]}"),
        Arguments.of("/by-ids", "{}"),
        Arguments.of("/by-ids", "{\"ids\":[" + ids101 + "]}"),
        Arguments.of("/by-ids", "{\"ids\":[0]}"),
        Arguments.of("/by-pmids", "{\"pmids\":[\"abc\"]}"),
        Arguments.of("/by-pmids", "{\"pmids\":[\"038001234\"]}"));
  }

  @Test
  @DisplayName("元素为 null：反序列化失败，400 且不触达服务")
  void should_reject_null_element_with_400_without_calling_service() {
    restClient
        .post()
        .uri("/_internal/publications/resolve/by-ids")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"ids\":[1,null]}")
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(service);
  }

  private String post(String path, String body) {
    return restClient
        .post()
        .uri("/_internal/publications/resolve" + path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private static PublicationResolveReadModel model(long id, boolean deleted) {
    return PublicationResolveReadModel.builder()
        .id(id)
        .pmid("38001234")
        .title("Empagliflozin in HFpEF")
        .venueName("The New England journal of medicine")
        .publicationYear(2021)
        .deleted(deleted)
        .build();
  }

  @Test
  @DisplayName("只有年份的文献：publishedDate 只输出年份，不补月日")
  void year_only_publication_has_no_fabricated_month_day() {
    PublicationResolveReadModel yearOnly =
        PublicationResolveReadModel.builder()
            .id(8L)
            .title("Year only")
            .publicationYear(2023)
            .publishedDate("2023")
            .build();
    when(service.resolveByIds(List.of(8L)))
        .thenReturn(List.of(PublicationResolution.of("8", yearOnly)));

    JsonNode first = JSON.readTree(post("/by-ids", "{\"ids\":[8]}")).get("items").get(0);

    assertThat(first.get("publishedDate").asString()).isEqualTo("2023");
    assertThat(first.get("year").asInt()).isEqualTo(2023);
  }
}
