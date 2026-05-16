package dev.linqibin.patra.registry.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.registry.adapter.rest.converter.DictionaryApiConverter;
import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveItemResp;
import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveReq;
import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveResp;
import dev.linqibin.patra.registry.app.service.DictionaryQueryService;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveItemQuery;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveStatus;
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

/// DictionaryEndpoint REST 接口集成测试。
///
/// 使用 Spring Boot 4.0 的 RestTestClient 进行 HTTP 层测试，验证：
///
/// - HTTP 请求/响应的序列化和反序列化
/// - 路径匹配和 Content-Type 处理
/// - 异常处理和错误响应格式
///
/// 与单元测试的区别：
///
/// - 单元测试直接调用 Controller 方法，不经过 HTTP 层
/// - 集成测试通过 RestTestClient 发起真实 HTTP 请求（基于 MockMvc）
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest
@Import(DictionaryEndpointImpl.class)
@AutoConfigureRestTestClient
@DisplayName("DictionaryEndpoint REST 接口集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DictionaryEndpointIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private DictionaryQueryService queryService;

  @MockitoBean private DictionaryApiConverter converter;

  @Nested
  @DisplayName("POST /_internal/dictionaries/resolve")
  class ResolveTests {

    @Test
    @DisplayName("应该成功解析字典值并返回 200 OK")
    void shouldResolveDictionaryAndReturn200() {
      // Given
      DictionaryResolveReq request =
          new DictionaryResolveReq("country", "iso_3166_1_alpha2", List.of("US"));

      DictionaryResolveQuery query =
          new DictionaryResolveQuery(
              "country",
              "ISO_3166_1_ALPHA2",
              List.of(
                  new DictionaryResolveItemQuery(
                      "US", "US", "United States", DictionaryResolveStatus.RESOLVED)));

      when(queryService.resolveBatch(eq("country"), eq("iso_3166_1_alpha2"), eq(List.of("US"))))
          .thenReturn(query);

      DictionaryResolveResp expectedResp =
          new DictionaryResolveResp(
              "country",
              "ISO_3166_1_ALPHA2",
              List.of(new DictionaryResolveItemResp("US", "US", "United States", "RESOLVED")));
      when(converter.toResp(any(DictionaryResolveQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .post()
          .uri("/_internal/dictionaries/resolve")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody(DictionaryResolveResp.class)
          .value(
              resp -> {
                assertThat(resp.typeCode()).isEqualTo("country");
                assertThat(resp.sourceStandard()).isEqualTo("ISO_3166_1_ALPHA2");
                assertThat(resp.items()).hasSize(1);

                DictionaryResolveItemResp item = resp.items().getFirst();
                assertThat(item.rawValue()).isEqualTo("US");
                assertThat(item.resolvedCode()).isEqualTo("US");
                assertThat(item.resolvedName()).isEqualTo("United States");
                assertThat(item.status()).isEqualTo("RESOLVED");
              });

      verify(queryService).resolveBatch("country", "iso_3166_1_alpha2", List.of("US"));
    }

    @Test
    @DisplayName("应该正确处理多个解析项")
    void shouldHandleMultipleResolveItems() {
      // Given
      DictionaryResolveReq request =
          new DictionaryResolveReq("country", "iso_3166_1_alpha2", List.of("US", "CN", "INVALID"));

      DictionaryResolveQuery query =
          new DictionaryResolveQuery(
              "country",
              "ISO_3166_1_ALPHA2",
              List.of(
                  new DictionaryResolveItemQuery(
                      "US", "US", "United States", DictionaryResolveStatus.RESOLVED),
                  new DictionaryResolveItemQuery(
                      "CN", "CN", "China", DictionaryResolveStatus.RESOLVED),
                  new DictionaryResolveItemQuery(
                      "INVALID", null, null, DictionaryResolveStatus.UNKNOWN)));

      when(queryService.resolveBatch(
              eq("country"), eq("iso_3166_1_alpha2"), eq(List.of("US", "CN", "INVALID"))))
          .thenReturn(query);

      DictionaryResolveResp expectedResp =
          new DictionaryResolveResp(
              "country",
              "ISO_3166_1_ALPHA2",
              List.of(
                  new DictionaryResolveItemResp("US", "US", "United States", "RESOLVED"),
                  new DictionaryResolveItemResp("CN", "CN", "China", "RESOLVED"),
                  new DictionaryResolveItemResp("INVALID", null, null, "UNKNOWN")));
      when(converter.toResp(any(DictionaryResolveQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .post()
          .uri("/_internal/dictionaries/resolve")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(DictionaryResolveResp.class)
          .value(
              resp -> {
                assertThat(resp.items()).hasSize(3);
                assertThat(resp.items().get(0).status()).isEqualTo("RESOLVED");
                assertThat(resp.items().get(1).status()).isEqualTo("RESOLVED");
                assertThat(resp.items().get(2).status()).isEqualTo("UNKNOWN");
              });
    }

    @Test
    @DisplayName("应该正确设置 Content-Type 为 application/json")
    void shouldSetCorrectContentType() {
      // Given
      DictionaryResolveReq request =
          new DictionaryResolveReq("language", "iso_639_1", List.of("en"));

      DictionaryResolveQuery query =
          new DictionaryResolveQuery(
              "language",
              "ISO_639_1",
              List.of(
                  new DictionaryResolveItemQuery(
                      "en", "en", "English", DictionaryResolveStatus.RESOLVED)));

      when(queryService.resolveBatch(eq("language"), eq("iso_639_1"), eq(List.of("en"))))
          .thenReturn(query);

      DictionaryResolveResp expectedResp =
          new DictionaryResolveResp(
              "language",
              "ISO_639_1",
              List.of(new DictionaryResolveItemResp("en", "en", "English", "RESOLVED")));
      when(converter.toResp(any(DictionaryResolveQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .post()
          .uri("/_internal/dictionaries/resolve")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("当 typeCode 为空时应该返回 422 Unprocessable Entity")
    void shouldReturn422WhenTypeCodeIsBlank() {
      // When & Then - 使用原始 JSON 绕过 Record 规范化
      restClient
          .post()
          .uri("/_internal/dictionaries/resolve")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                    {
                        "typeCode": "",
                        "sourceStandard": "iso_3166_1_alpha2",
                        "rawValues": ["US"]
                    }
                    """)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }

    @Test
    @DisplayName("当 sourceStandard 为空时应该返回 422 Unprocessable Entity")
    void shouldReturn422WhenSourceStandardIsBlank() {
      // When & Then
      restClient
          .post()
          .uri("/_internal/dictionaries/resolve")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                    {
                        "typeCode": "country",
                        "sourceStandard": "   ",
                        "rawValues": ["US"]
                    }
                    """)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }

    @Test
    @DisplayName("当 rawValues 为 null 时应该返回 422 Unprocessable Entity")
    void shouldReturn422WhenRawValuesIsNull() {
      // When & Then
      restClient
          .post()
          .uri("/_internal/dictionaries/resolve")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                    {
                        "typeCode": "country",
                        "sourceStandard": "iso_3166_1_alpha2",
                        "rawValues": null
                    }
                    """)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }
  }
}
