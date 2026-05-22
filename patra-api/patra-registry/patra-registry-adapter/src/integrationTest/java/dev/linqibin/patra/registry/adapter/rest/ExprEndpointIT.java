package dev.linqibin.patra.registry.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.registry.adapter.rest.converter.ExprApiConverter;
import dev.linqibin.patra.registry.api.dto.expr.ExprSnapshotResp;
import dev.linqibin.patra.registry.app.service.ExprQueryService;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import java.time.Instant;
import java.util.Collections;
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

/// ExprEndpoint REST 接口集成测试。
///
/// 使用 Spring Boot 4.0 的 RestTestClient 进行 HTTP 层测试，验证：
///
/// - HTTP 请求/响应的序列化和反序列化
/// - 路径匹配和 Query Parameter 处理
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
@Import(ExprEndpointImpl.class)
@AutoConfigureRestTestClient
@DisplayName("ExprEndpoint REST 接口集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ExprEndpointIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private ExprQueryService queryService;

  @MockitoBean private ExprApiConverter converter;

  @Nested
  @DisplayName("GET /_internal/expr/snapshot")
  class GetSnapshotTests {

    @Test
    @DisplayName("应该成功返回表达式快照并返回 200 OK")
    void shouldReturnExprSnapshotWith200() {
      // Given
      String provenanceCode = "PUBMED";
      String operationType = "HARVEST";
      String endpointName = "search";
      Instant at = Instant.parse("2024-01-01T00:00:00Z");

      ExprSnapshotQuery queryResult = createMockQuery();
      when(queryService.loadSnapshot(
              eq(provenanceCode), eq(operationType), eq(endpointName), eq(at)))
          .thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(any(ExprSnapshotQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/_internal/expr/snapshot")
                      .queryParam("provenanceCode", provenanceCode)
                      .queryParam("operationType", operationType)
                      .queryParam("endpointName", endpointName)
                      .queryParam("at", at.toString())
                      .build())
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody(ExprSnapshotResp.class)
          .value(
              resp -> {
                assertThat(resp).isNotNull();
                assertThat(resp.fields()).isNotNull();
                assertThat(resp.capabilities()).isNotNull();
              });

      verify(queryService).loadSnapshot(provenanceCode, operationType, endpointName, at);
    }

    @Test
    @DisplayName("应该支持只提供必填参数 provenanceCode")
    void shouldSupportOnlyRequiredParameter() {
      // Given
      String provenanceCode = "EPMC";

      ExprSnapshotQuery queryResult = createMockQuery();
      when(queryService.loadSnapshot(eq(provenanceCode), isNull(), isNull(), isNull()))
          .thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(any(ExprSnapshotQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/_internal/expr/snapshot")
                      .queryParam("provenanceCode", provenanceCode)
                      .build())
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(ExprSnapshotResp.class)
          .value(resp -> assertThat(resp).isNotNull());

      verify(queryService).loadSnapshot(provenanceCode, null, null, null);
    }

    @Test
    @DisplayName("应该正确传递时态切片参数 at")
    void shouldPassTemporalParameter() {
      // Given
      String provenanceCode = "ARXIV";
      Instant historicalTime = Instant.parse("2023-06-15T12:00:00Z");

      ExprSnapshotQuery queryResult = createMockQuery();
      when(queryService.loadSnapshot(eq(provenanceCode), isNull(), isNull(), eq(historicalTime)))
          .thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(any(ExprSnapshotQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/_internal/expr/snapshot")
                      .queryParam("provenanceCode", provenanceCode)
                      .queryParam("at", historicalTime.toString())
                      .build())
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(ExprSnapshotResp.class)
          .value(resp -> assertThat(resp).isNotNull());

      verify(queryService).loadSnapshot(provenanceCode, null, null, historicalTime);
    }

    @Test
    @DisplayName("应该正确设置 Content-Type 为 application/json")
    void shouldSetCorrectContentType() {
      // Given
      String provenanceCode = "CROSSREF";

      ExprSnapshotQuery queryResult = createMockQuery();
      when(queryService.loadSnapshot(eq(provenanceCode), isNull(), isNull(), isNull()))
          .thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(any(ExprSnapshotQuery.class))).thenReturn(expectedResp);

      // When & Then
      restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/_internal/expr/snapshot")
                      .queryParam("provenanceCode", provenanceCode)
                      .build())
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("缺少必填参数 provenanceCode 时应该返回 400 Bad Request")
    void shouldReturn400WhenMissingRequiredParameter() {
      // When & Then
      restClient.get().uri("/_internal/expr/snapshot").exchange().expectStatus().isBadRequest();
    }
  }

  // ========== 测试数据构建助手 ==========

  private ExprSnapshotQuery createMockQuery() {
    return new ExprSnapshotQuery(
        Collections.emptyList(), // fields
        Collections.emptyList(), // capabilities
        Collections.emptyList(), // renderRules
        Collections.emptyList() // paramMappings
        );
  }

  private ExprSnapshotResp createMockResponse() {
    return new ExprSnapshotResp(
        Collections.emptyList(), // fields
        Collections.emptyList(), // capabilities
        Collections.emptyList(), // renderRules
        Collections.emptyList() // paramMappings
        );
  }
}
