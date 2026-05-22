package dev.linqibin.patra.registry.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.registry.adapter.rest.converter.ProvenanceApiConverter;
import dev.linqibin.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import dev.linqibin.patra.registry.api.dto.provenance.ProvenanceResp;
import dev.linqibin.patra.registry.app.service.ProvenanceQueryService;
import dev.linqibin.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import dev.linqibin.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// ProvenanceEndpoint REST 接口集成测试。
///
/// 使用 Spring Boot 4.0 的 RestTestClient 进行 HTTP 层测试，验证：
///
/// - HTTP 请求/响应的序列化和反序列化
/// - 路径匹配和路径变量处理
/// - Query Parameter 处理
/// - Content-Type 正确设置
///
/// 与单元测试的区别：
///
/// - 单元测试直接调用 Controller 方法，不经过 HTTP 层
/// - 集成测试通过 RestTestClient 发起真实 HTTP 请求（基于 MockMvc）
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest
@Import(ProvenanceEndpointImpl.class)
@AutoConfigureRestTestClient
@DisplayName("ProvenanceEndpoint REST 接口集成测试")
class ProvenanceEndpointIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private ProvenanceQueryService queryService;

  @MockitoBean private ProvenanceApiConverter converter;

  @Nested
  @DisplayName("GET /_internal/provenances")
  class ListProvenancesTests {

    @Test
    @DisplayName("应该成功返回所有数据源列表并返回 200 OK")
    void shouldReturnProvenanceListWith200() {
      // Given
      List<ProvenanceQuery> queryResults =
          List.of(createMockProvenanceQuery("PUBMED"), createMockProvenanceQuery("EPMC"));
      when(queryService.listProvenances()).thenReturn(queryResults);

      List<ProvenanceResp> expectedResps =
          List.of(createMockProvenanceResp("PUBMED"), createMockProvenanceResp("EPMC"));
      when(converter.toResp(queryResults)).thenReturn(expectedResps);

      // When & Then
      restClient
          .get()
          .uri("/_internal/provenances")
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(2)
          .jsonPath("$[0].code")
          .isEqualTo("PUBMED")
          .jsonPath("$[1].code")
          .isEqualTo("EPMC");

      verify(queryService).listProvenances();
    }

    @Test
    @DisplayName("当没有数据源时应该返回空列表")
    void shouldReturnEmptyListWhenNoProvenances() {
      // Given
      when(queryService.listProvenances()).thenReturn(Collections.emptyList());
      when(converter.toResp(Collections.emptyList())).thenReturn(Collections.emptyList());

      // When & Then
      restClient
          .get()
          .uri("/_internal/provenances")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(0);

      verify(queryService).listProvenances();
    }

    @Test
    @DisplayName("应该正确设置 Content-Type 为 application/json")
    void shouldSetCorrectContentType() {
      // Given
      when(queryService.listProvenances()).thenReturn(Collections.emptyList());
      when(converter.toResp(Collections.emptyList())).thenReturn(Collections.emptyList());

      // When & Then
      restClient
          .get()
          .uri("/_internal/provenances")
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON);
    }
  }

  @Nested
  @DisplayName("GET /_internal/provenances/{code}")
  class GetProvenanceTests {

    @Test
    @DisplayName("应该成功返回单个数据源并返回 200 OK")
    void shouldReturnSingleProvenanceWith200() {
      // Given
      ProvenanceCode code = ProvenanceCode.PUBMED;
      ProvenanceQuery queryResult = createMockProvenanceQuery("PUBMED");
      when(queryService.findProvenance(eq(code))).thenReturn(Optional.of(queryResult));

      ProvenanceResp expectedResp = createMockProvenanceResp("PUBMED");
      when(converter.toResp(any(ProvenanceQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri("/_internal/provenances/PUBMED")
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody(ProvenanceResp.class)
          .value(
              resp -> {
                assertThat(resp.code()).isEqualTo("PUBMED");
                assertThat(resp.name()).isEqualTo("PUBMED Source");
                assertThat(resp.active()).isTrue();
              });

      verify(queryService).findProvenance(code);
    }

    @Test
    @DisplayName("应该支持不同的数据源代码")
    void shouldSupportDifferentProvenanceCodes() {
      // Given
      ProvenanceCode code = ProvenanceCode.EPMC;
      ProvenanceQuery queryResult = createMockProvenanceQuery("EPMC");
      when(queryService.findProvenance(eq(code))).thenReturn(Optional.of(queryResult));

      ProvenanceResp expectedResp = createMockProvenanceResp("EPMC");
      when(converter.toResp(any(ProvenanceQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri("/_internal/provenances/EPMC")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(ProvenanceResp.class)
          .value(resp -> assertThat(resp.code()).isEqualTo("EPMC"));

      verify(queryService).findProvenance(code);
    }

    @Test
    @DisplayName("当数据源不存在时应该返回 404 Not Found")
    void shouldReturn404WhenProvenanceNotFound() {
      // Given
      ProvenanceCode code = ProvenanceCode.PUBMED;
      when(queryService.findProvenance(eq(code))).thenReturn(Optional.empty());

      // When & Then
      restClient.get().uri("/_internal/provenances/PUBMED").exchange().expectStatus().isNotFound();

      verify(queryService).findProvenance(code);
    }
  }

  @Nested
  @DisplayName("GET /_internal/provenances/{code}/config")
  class GetConfigurationTests {

    @Test
    @DisplayName("应该成功返回配置聚合并返回 200 OK")
    void shouldReturnConfigurationWith200() {
      // Given
      ProvenanceCode code = ProvenanceCode.PUBMED;
      String operationType = "HARVEST";
      Instant at = Instant.parse("2024-01-01T00:00:00Z");

      ProvenanceConfigQuery queryResult = createMockConfigQuery();
      when(queryService.loadConfiguration(eq(code), eq(operationType), eq(at)))
          .thenReturn(Optional.of(queryResult));

      ProvenanceConfigResp expectedResp = createMockConfigResp();
      when(converter.toResp(any(ProvenanceConfigQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/_internal/provenances/PUBMED/config")
                      .queryParam("operationType", operationType)
                      .queryParam("at", at.toString())
                      .build())
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody(ProvenanceConfigResp.class)
          .value(
              resp -> {
                assertThat(resp).isNotNull();
                assertThat(resp.provenance()).isNotNull();
                assertThat(resp.provenance().code()).isEqualTo("PUBMED");
              });

      verify(queryService).loadConfiguration(code, operationType, at);
    }

    @Test
    @DisplayName("应该支持可选参数为空")
    void shouldSupportOptionalParametersAsNull() {
      // Given
      ProvenanceCode code = ProvenanceCode.EPMC;

      ProvenanceConfigQuery queryResult = createMockConfigQuery();
      when(queryService.loadConfiguration(eq(code), isNull(), isNull()))
          .thenReturn(Optional.of(queryResult));

      ProvenanceConfigResp expectedResp = createMockConfigResp();
      when(converter.toResp(any(ProvenanceConfigQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri("/_internal/provenances/EPMC/config")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(ProvenanceConfigResp.class)
          .value(resp -> assertThat(resp).isNotNull());

      verify(queryService).loadConfiguration(code, null, null);
    }

    @Test
    @DisplayName("应该正确传递时态切片参数 at")
    void shouldPassTemporalParameter() {
      // Given
      ProvenanceCode code = ProvenanceCode.PUBMED;
      Instant historicalTime = Instant.parse("2023-06-15T12:00:00Z");

      ProvenanceConfigQuery queryResult = createMockConfigQuery();
      when(queryService.loadConfiguration(eq(code), isNull(), eq(historicalTime)))
          .thenReturn(Optional.of(queryResult));

      ProvenanceConfigResp expectedResp = createMockConfigResp();
      when(converter.toResp(any(ProvenanceConfigQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/_internal/provenances/PUBMED/config")
                      .queryParam("at", historicalTime.toString())
                      .build())
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(ProvenanceConfigResp.class)
          .value(resp -> assertThat(resp).isNotNull());

      verify(queryService).loadConfiguration(code, null, historicalTime);
    }

    @Test
    @DisplayName("应该正确设置 Content-Type 为 application/json")
    void shouldSetCorrectContentType() {
      // Given
      ProvenanceCode code = ProvenanceCode.PUBMED;

      ProvenanceConfigQuery queryResult = createMockConfigQuery();
      when(queryService.loadConfiguration(eq(code), isNull(), isNull()))
          .thenReturn(Optional.of(queryResult));

      ProvenanceConfigResp expectedResp = createMockConfigResp();
      when(converter.toResp(any(ProvenanceConfigQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri("/_internal/provenances/PUBMED/config")
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("当配置不存在时应该返回 404 Not Found")
    void shouldReturn404WhenConfigNotFound() {
      // Given
      ProvenanceCode code = ProvenanceCode.EPMC;
      when(queryService.loadConfiguration(eq(code), isNull(), isNull()))
          .thenReturn(Optional.empty());

      // When & Then
      restClient
          .get()
          .uri("/_internal/provenances/EPMC/config")
          .exchange()
          .expectStatus()
          .isNotFound();

      verify(queryService).loadConfiguration(code, null, null);
    }
  }

  // ========== 测试数据构建助手 ==========

  private ProvenanceQuery createMockProvenanceQuery(String code) {
    return new ProvenanceQuery(
        1L,
        code,
        code + " Source",
        "https://example.com",
        "UTC",
        "https://docs.example.com",
        true,
        "ACTIVE");
  }

  private ProvenanceResp createMockProvenanceResp(String code) {
    return new ProvenanceResp(
        1L,
        code,
        code + " Source",
        "https://example.com",
        "UTC",
        "https://docs.example.com",
        true,
        "ACTIVE");
  }

  private ProvenanceConfigQuery createMockConfigQuery() {
    return new ProvenanceConfigQuery(
        createMockProvenanceQuery("PUBMED"), null, null, null, null, null, null);
  }

  private ProvenanceConfigResp createMockConfigResp() {
    return new ProvenanceConfigResp(
        createMockProvenanceResp("PUBMED"), null, null, null, null, null, null);
  }
}
