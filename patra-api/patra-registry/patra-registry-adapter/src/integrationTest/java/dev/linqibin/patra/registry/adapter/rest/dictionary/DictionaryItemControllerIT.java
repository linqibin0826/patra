package dev.linqibin.patra.registry.adapter.rest.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.registry.adapter.rest.dictionary.mapper.DictionaryItemApiConverter;
import dev.linqibin.patra.registry.adapter.rest.dictionary.response.DictionaryItemListResponse;
import dev.linqibin.patra.registry.adapter.rest.dictionary.response.DictionaryItemResponse;
import dev.linqibin.patra.registry.app.service.DictionaryQueryService;
import dev.linqibin.patra.registry.domain.exception.dictionary.DictionaryTypeNotFoundException;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemListResult;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemSummary;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// DictionaryItemController REST 接口集成测试。
///
/// 使用 @WebMvcTest 切片测试验证 HTTP 层行为：
///
/// - 请求路由和参数绑定（typeCode 必填、labelStandard 可选）
/// - 响应序列化和 Content-Type
/// - 异常映射（NOT_FOUND → 404）
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest
@Import(DictionaryItemController.class)
@AutoConfigureRestTestClient
@DisplayName("DictionaryItemController REST 接口集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DictionaryItemControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private DictionaryQueryService queryService;

  @MockitoBean private DictionaryItemApiConverter converter;

  @Nested
  @DisplayName("GET /dictionaries/items")
  class ListItemsTests {

    @Test
    @DisplayName("仅 typeCode 时应返回 200 + 字典项列表（无 label）")
    void shouldReturnItemsWithoutLabel() {
      // Given
      var domainResult =
          new DictionaryItemListResult(
              "country",
              null,
              List.of(
                  new DictionaryItemSummary("CN", "China", null, 156),
                  new DictionaryItemSummary("US", "United States of America", null, 840)));

      when(queryService.listItems(eq("country"), isNull())).thenReturn(domainResult);

      var expectedResp =
          new DictionaryItemListResponse(
              "country",
              null,
              List.of(
                  new DictionaryItemResponse("CN", "China", null, 156),
                  new DictionaryItemResponse("US", "United States of America", null, 840)));
      when(converter.toResponse(domainResult)).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri("/dictionaries/items?typeCode=country")
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody(DictionaryItemListResponse.class)
          .value(
              resp -> {
                assertThat(resp.typeCode()).isEqualTo("country");
                assertThat(resp.labelStandard()).isNull();
                assertThat(resp.items()).hasSize(2);
                assertThat(resp.items().getFirst().code()).isEqualTo("CN");
                assertThat(resp.items().getFirst().name()).isEqualTo("China");
                assertThat(resp.items().getFirst().label()).isNull();
                assertThat(resp.items().get(1).code()).isEqualTo("US");
              });

      verify(queryService).listItems("country", null);
    }

    @Test
    @DisplayName("带 labelStandard 时应返回 200 + 字典项列表（含 label）")
    void shouldReturnItemsWithLabel() {
      // Given
      var domainResult =
          new DictionaryItemListResult(
              "country",
              "NAME_ZH",
              List.of(
                  new DictionaryItemSummary("CN", "China", "中国", 156),
                  new DictionaryItemSummary("US", "United States of America", "美国", 840)));

      when(queryService.listItems(eq("country"), eq("NAME_ZH"))).thenReturn(domainResult);

      var expectedResp =
          new DictionaryItemListResponse(
              "country",
              "NAME_ZH",
              List.of(
                  new DictionaryItemResponse("CN", "China", "中国", 156),
                  new DictionaryItemResponse("US", "United States of America", "美国", 840)));
      when(converter.toResponse(domainResult)).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri("/dictionaries/items?typeCode=country&labelStandard=NAME_ZH")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(DictionaryItemListResponse.class)
          .value(
              resp -> {
                assertThat(resp.typeCode()).isEqualTo("country");
                assertThat(resp.labelStandard()).isEqualTo("NAME_ZH");
                assertThat(resp.items()).hasSize(2);
                assertThat(resp.items().getFirst().label()).isEqualTo("中国");
                assertThat(resp.items().get(1).label()).isEqualTo("美国");
              });

      verify(queryService).listItems("country", "NAME_ZH");
    }

    @Test
    @DisplayName("当字典类型不存在时应返回 404 Not Found")
    void shouldReturn404WhenTypeCodeNotFound() {
      // Given
      when(queryService.listItems(eq("nonexistent"), isNull()))
          .thenThrow(new DictionaryTypeNotFoundException("nonexistent"));

      // When & Then
      restClient
          .get()
          .uri("/dictionaries/items?typeCode=nonexistent")
          .exchange()
          .expectStatus()
          .isNotFound();
    }

    @Test
    @DisplayName("缺少 typeCode 参数时应返回 400 Bad Request")
    void shouldReturn400WhenTypeCodeMissing() {
      // When & Then
      restClient.get().uri("/dictionaries/items").exchange().expectStatus().isBadRequest();
    }
  }
}
