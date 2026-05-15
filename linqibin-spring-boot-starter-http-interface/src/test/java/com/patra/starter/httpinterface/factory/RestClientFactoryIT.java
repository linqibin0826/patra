package com.patra.starter.httpinterface.factory;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.patra.starter.httpinterface.config.HttpInterfaceAutoConfiguration;
import dev.linqibin.commons.error.remote.RemoteCallException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// RestClientFactory 集成测试。
///
/// 使用 WireMock 发起真实 HTTP 调用，验证 `RestClient.Builder.clone()` 后配置是否完整保留。
///
/// **测试目标：**
///
/// 1. 验证 clone() 后 RestClient 能正常工作
/// 2. 验证错误处理器在 clone() 后能正确转换异常
/// 3. 验证超时配置在 clone() 后正确应用
///
/// > **注意**：TraceId 传播由 OpenTelemetry Java Agent 自动处理，无需在此测试。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(classes = RestClientFactoryIT.TestConfiguration.class)
@WireMockTest
@TestPropertySource(
    properties = {
      "patra.http.interface.connect-timeout=5s",
      "patra.http.interface.read-timeout=10s",
      "patra.http.interface.error-handling.tolerant=true",
      "patra.http.interface.error-handling.problem-detail-enabled=true"
    })
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@DisplayName("RestClientFactory 集成测试")
class RestClientFactoryIT {

  @Autowired private RestClientFactory factory;

  @Autowired
  @Qualifier("httpInterfaceRestClientBuilder")
  private RestClient.Builder restClientBuilder;

  @Nested
  @DisplayName("clone() 后配置完整性验证")
  class CloneConfigurationTests {

    @Test
    @DisplayName("clone() 后应能成功发起 HTTP 请求并获取响应")
    void shouldMakeSuccessfulRequestAfterClone(WireMockRuntimeInfo wmRuntimeInfo) {
      // Given - 配置 WireMock stub
      stubFor(
          get(urlEqualTo("/health"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                      .withBody("{\"status\":\"UP\"}")));

      // When - 通过 Factory 创建 RestClient（内部会调用 clone）
      RestClient client =
          factory.createRestClient(restClientBuilder, "test", wmRuntimeInfo.getHttpBaseUrl());

      // Then - 验证能成功调用并获取响应
      String response = client.get().uri("/health").retrieve().body(String.class);

      assertThat(response).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("clone() 后 ProblemDetail 错误处理器应生效")
    void shouldApplyErrorHandlerAfterClone(WireMockRuntimeInfo wmRuntimeInfo) {
      // Given - 模拟 ProblemDetail 格式的错误响应
      stubFor(
          get(urlEqualTo("/not-found"))
              .willReturn(
                  aResponse()
                      .withStatus(404)
                      .withHeader("Content-Type", "application/problem+json")
                      .withBody(
                          """
                          {
                            "type": "about:blank",
                            "title": "Not Found",
                            "status": 404,
                            "detail": "资源未找到"
                          }
                          """)));

      RestClient client =
          factory.createRestClient(restClientBuilder, "test", wmRuntimeInfo.getHttpBaseUrl());

      // When & Then - 应抛出 RemoteCallException 并正确解析 ProblemDetail
      assertThatThrownBy(() -> client.get().uri("/not-found").retrieve().body(String.class))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getHttpStatus()).isEqualTo(404);
                assertThat(rce.getMessage()).isEqualTo("资源未找到");
              });
    }

    @Test
    @DisplayName("clone() 后非 ProblemDetail 错误响应也应转换为 RemoteCallException")
    void shouldHandleNonProblemDetailErrorAfterClone(WireMockRuntimeInfo wmRuntimeInfo) {
      // Given - 模拟普通 JSON 错误响应（非 ProblemDetail 格式）
      stubFor(
          get(urlEqualTo("/plain-error"))
              .willReturn(
                  aResponse()
                      .withStatus(500)
                      .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                      .withBody("{\"error\":\"Internal Server Error\"}")));

      RestClient client =
          factory.createRestClient(restClientBuilder, "test", wmRuntimeInfo.getHttpBaseUrl());

      // When & Then - 应抛出 RemoteCallException（容错模式下包装为通用异常）
      assertThatThrownBy(() -> client.get().uri("/plain-error").retrieve().body(String.class))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getHttpStatus()).isEqualTo(500);
              });
    }
  }

  @Nested
  @DisplayName("多次 clone() 验证")
  class MultipleCloneTests {

    @Test
    @DisplayName("多次调用 createRestClient 应创建独立的 RestClient 实例")
    void shouldCreateIndependentClientsOnMultipleCalls(WireMockRuntimeInfo wmRuntimeInfo) {
      // Given
      stubFor(get(urlEqualTo("/client1")).willReturn(aResponse().withStatus(200).withBody("OK1")));
      stubFor(get(urlEqualTo("/client2")).willReturn(aResponse().withStatus(200).withBody("OK2")));

      // When - 创建两个独立的 RestClient
      RestClient client1 =
          factory.createRestClient(restClientBuilder, "test", wmRuntimeInfo.getHttpBaseUrl());
      RestClient client2 =
          factory.createRestClient(restClientBuilder, "test", wmRuntimeInfo.getHttpBaseUrl());

      // Then - 两个客户端应独立工作
      String response1 = client1.get().uri("/client1").retrieve().body(String.class);
      String response2 = client2.get().uri("/client2").retrieve().body(String.class);

      assertThat(response1).isEqualTo("OK1");
      assertThat(response2).isEqualTo("OK2");
    }
  }

  // ===== 测试配置类 =====

  /// 测试专用 Spring Boot 配置类。
  ///
  /// 加载 HTTP Interface 自动配置，排除不必要的自动配置（DataSource、JPA、Flyway 等）。
  @org.springframework.boot.SpringBootConfiguration
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration(
      exclude = {
        org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
        org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration.class,
        org.springframework.cloud.loadbalancer.config.LoadBalancerAutoConfiguration.class
      })
  @ImportAutoConfiguration(HttpInterfaceAutoConfiguration.class)
  static class TestConfiguration {

    /// 提供 ObjectMapper Bean（HTTP Interface 自动配置依赖）
    @org.springframework.context.annotation.Bean
    public ObjectMapper objectMapper() {
      return JsonMapper.builder().build();
    }
  }
}
