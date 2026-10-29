package dev.patra.catalog.adapter.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.api.venue.dto.VenueResolveResp;
import dev.patra.catalog.app.usecase.venue.query.VenueResolveQueryService;
import dev.patra.catalog.app.usecase.venue.query.dto.VenueResolution;
import dev.patra.catalog.domain.model.read.venue.VenueResolveReadModel;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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

/// `VenueEndpointImpl` REST 切片测试：`POST /_internal/venues/resolve/by-ids|by-nlm-ids`。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(controllers = VenueEndpointImpl.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import(VenueEndpointImpl.class)
@AutoConfigureRestTestClient
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("VenueEndpoint REST 切片测试")
class VenueEndpointIT {

  /// 与 interaction 侧 `RestClient` 默认一致的映射器：用来验证字符串 ID 能读回 `Long`。
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final long BIG_ID = 9_007_199_254_740_993L;

  @Autowired private RestTestClient restClient;

  @MockitoBean private VenueResolveQueryService service;

  @Test
  @DisplayName("按 ID：按服务顺序输出，id 为字符串，不存在的行除 key 外全为 null")
  void should_return_rows_in_service_order_with_string_ids() {
    when(service.resolveByIds(List.of(BIG_ID, 1L)))
        .thenReturn(
            List.of(
                VenueResolution.of(Long.toString(BIG_ID), model(BIG_ID, "0404401", false)),
                VenueResolution.of("1", null)));

    JsonNode json = JSON.readTree(post("/by-ids", "{\"ids\":[" + BIG_ID + ",1]}"));

    JsonNode first = json.get("items").get(0);
    assertThat(first.get("key").asString()).isEqualTo("9007199254740993");
    assertThat(first.get("exists").asBoolean()).isTrue();
    assertThat(first.get("state").asString()).isEqualTo("ACTIVE");
    assertThat(first.get("id").isString()).isTrue();
    assertThat(first.get("id").asString()).isEqualTo("9007199254740993");
    assertThat(first.get("nlmId").asString()).isEqualTo("0404401");
    assertThat(first.get("title").asString())
        .isEqualTo("Schweizerische medizinische Wochenschrift");
    assertThat(first.get("abbreviatedTitle").asString()).isEqualTo("Swiss Med Wkly");
    JsonNode missing = json.get("items").get(1);
    assertThat(missing.get("key").asString()).isEqualTo("1");
    assertThat(missing.get("exists").asBoolean()).isFalse();
    for (String field : List.of("state", "id", "nlmId", "title", "abbreviatedTitle")) {
      assertThat(missing.get(field).isNull()).as(field).isTrue();
    }
  }

  @Test
  @DisplayName("按 NLM：前导零原样；请求副 NLM 时 key 是副 NLM、nlmId 是主 NLM")
  void should_keep_leading_zero_and_return_primary_for_secondary_key() {
    VenueResolveReadModel swiss = model(9L, "0404401", false);
    when(service.resolveByNlmIds(List.of("0404401", "100970884")))
        .thenReturn(
            List.of(VenueResolution.of("0404401", swiss), VenueResolution.of("100970884", swiss)));

    JsonNode items =
        JSON.readTree(post("/by-nlm-ids", "{\"nlmIds\":[\"0404401\",\"100970884\"]}")).get("items");

    assertThat(items.get(0).get("key").asString()).isEqualTo("0404401");
    assertThat(items.get(0).get("nlmId").asString()).isEqualTo("0404401");
    assertThat(items.get(1).get("key").asString()).isEqualTo("100970884");
    assertThat(items.get(1).get("nlmId").asString()).isEqualTo("0404401");
    verify(service).resolveByNlmIds(List.of("0404401", "100970884"));
  }

  @Test
  @DisplayName("软删映射为 DELETED，刊名照样有值")
  void should_map_soft_deleted_to_deleted_state() {
    when(service.resolveByIds(List.of(7L)))
        .thenReturn(List.of(VenueResolution.of("7", model(7L, "0000003R", true))));

    JsonNode first = JSON.readTree(post("/by-ids", "{\"ids\":[7]}")).get("items").get(0);

    assertThat(first.get("state").asString()).isEqualTo("DELETED");
    assertThat(first.get("title").asString())
        .isEqualTo("Schweizerische medizinische Wochenschrift");
  }

  @Test
  @DisplayName("响应体用默认 JsonMapper 读回契约 record，字符串 id 变回 Long")
  void should_round_trip_id_as_long_with_default_json_mapper() {
    when(service.resolveByIds(List.of(BIG_ID)))
        .thenReturn(
            List.of(VenueResolution.of(Long.toString(BIG_ID), model(BIG_ID, "0404401", false))));

    VenueResolveResp resp =
        JSON.readValue(post("/by-ids", "{\"ids\":[" + BIG_ID + "]}"), VenueResolveResp.class);

    assertThat(resp.items().get(0).id()).isEqualTo(BIG_ID);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"0404401", "2985213R", "9300367A", "9918523075306676"})
  @DisplayName("NLM ID 的真实写法都能通过校验：前导零、R / A 后缀、16 位新式 ID")
  void should_accept_valid_nlm_shapes(String nlmId) {
    when(service.resolveByNlmIds(anyList())).thenReturn(List.of(VenueResolution.of(nlmId, null)));

    post("/by-nlm-ids", "{\"nlmIds\":[\"" + nlmId + "\"]}");

    verify(service).resolveByNlmIds(List.of(nlmId));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("invalidRequests")
  @DisplayName("非法请求 422，不触达服务")
  void should_reject_invalid_requests_with_422_without_calling_service(String path, String body) {
    restClient
        .post()
        .uri("/_internal/venues/resolve" + path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    verifyNoInteractions(service);
  }

  /// 非法请求：空、缺失、超过 100、非正整数；NLM ID 小写后缀、中间夹字母、空串、尾随空格、超长。
  ///
  /// @return 路径与请求体
  static List<Arguments> invalidRequests() {
    String ids101 =
        LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
    String nlmIds101 =
        IntStream.rangeClosed(1, 101)
            .mapToObj(i -> "\"" + (1_000_000 + i) + "\"")
            .collect(Collectors.joining(","));
    return List.of(
        Arguments.of("/by-ids", "{\"ids\":[]}"),
        Arguments.of("/by-ids", "{}"),
        Arguments.of("/by-ids", "{\"ids\":[" + ids101 + "]}"),
        Arguments.of("/by-ids", "{\"ids\":[0]}"),
        Arguments.of("/by-nlm-ids", "{\"nlmIds\":[]}"),
        Arguments.of("/by-nlm-ids", "{\"nlmIds\":[\"2985213r\"]}"),
        Arguments.of("/by-nlm-ids", "{\"nlmIds\":[\"12A34\"]}"),
        Arguments.of("/by-nlm-ids", "{\"nlmIds\":[\"\"]}"),
        Arguments.of("/by-nlm-ids", "{\"nlmIds\":[\"0404401 \"]}"),
        Arguments.of("/by-nlm-ids", "{\"nlmIds\":[\"12345678901234567890\"]}"),
        Arguments.of("/by-nlm-ids", "{\"nlmIds\":[" + nlmIds101 + "]}"));
  }

  @Test
  @DisplayName("元素为 null：反序列化失败，400 且不触达服务")
  void should_reject_null_element_with_400_without_calling_service() {
    restClient
        .post()
        .uri("/_internal/venues/resolve/by-ids")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"ids\":[1,null]}")
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(service);
  }

  /// 发一个解析请求，断言 200 并取响应体。
  ///
  /// @param path `/by-ids` 或 `/by-nlm-ids`
  /// @param body 请求体
  /// @return 响应体
  private String post(String path, String body) {
    return restClient
        .post()
        .uri("/_internal/venues/resolve" + path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  /// 读模型夹具。
  ///
  /// @param id 期刊 ID
  /// @param nlmId 主 NLM
  /// @param deleted 是否软删
  /// @return 读模型
  private static VenueResolveReadModel model(long id, String nlmId, boolean deleted) {
    return VenueResolveReadModel.builder()
        .id(id)
        .nlmId(nlmId)
        .title("Schweizerische medizinische Wochenschrift")
        .abbreviatedTitle("Swiss Med Wkly")
        .deleted(deleted)
        .build();
  }
}
