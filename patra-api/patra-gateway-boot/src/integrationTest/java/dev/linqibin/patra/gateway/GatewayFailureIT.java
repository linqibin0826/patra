package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 网关自身产生的错误（设计第 8 节）：未知路由 404、无实例 503、连接被拒 503、读超时 504，
/// 都是带错误码的 ProblemDetail；以及读超时是总时长这一接受的限制（第 6 节）。
///
/// `test` profile 把读超时压到 1 秒。三条路由各扮一个角色：catalog 指向 WireMock，
/// registry 指向没人监听的端口，ingest 不配实例。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})
class GatewayFailureIT {

  private static final int STREAM_BODY_LENGTH = 10_000;

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @Autowired private RestTestClient restClient;
  @Autowired private Environment environment;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-registry[0].uri",
        () -> "http://127.0.0.1:1");
  }

  @Test
  void should_return_problem_detail_404_for_unknown_route() {
    restClient
        .get()
        .uri("/nowhere")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0404")
        .jsonPath("$.status")
        .isEqualTo(404);
  }

  @Test
  void should_return_503_when_no_instance_is_available() {
    restClient
        .get()
        .uri("/patra-ingest/plans")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0503")
        .jsonPath("$.detail")
        .isEqualTo("下游服务暂时不可用");
  }

  @Test
  void should_return_503_when_connection_is_refused() {
    restClient
        .get()
        .uri("/patra-registry/provenance/pubmed")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0503")
        .jsonPath("$.detail")
        .isEqualTo("下游服务暂时不可用");
  }

  @Test
  void should_return_504_when_downstream_does_not_answer_in_time() {
    catalog.stubFor(get(urlPathEqualTo("/slow")).willReturn(okJson("{}").withFixedDelay(2_000)));

    restClient
        .get()
        .uri("/patra-catalog/slow")
        .exchange()
        .expectStatus()
        .isEqualTo(504)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0504")
        .jsonPath("$.detail")
        .isEqualTo("下游服务响应超时");
  }

  /// 钉住接受的限制之一：流式响应（逐块 flush，已提交）在读超时时被切断。此时状态码已经发出，
  /// 网关不再往后拼任何东西，Tomcat 干净地结束分块传输：客户端拿到 200 和一段只含已到字节的半截响应体。
  @Test
  void should_end_committed_streaming_response_cleanly_at_read_timeout() throws Exception {
    catalog.stubFor(
        get(urlPathEqualTo("/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody("x".repeat(STREAM_BODY_LENGTH))
                    .withChunkedDribbleDelay(10, 3_000)));

    HttpResponse<String> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response =
          client.send(
              HttpRequest.newBuilder(URI.create(gatewayUrl("/patra-catalog/stream"))).build(),
              HttpResponse.BodyHandlers.ofString());
    }

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isNotEmpty().hasSizeLessThan(STREAM_BODY_LENGTH).matches("x+");
  }

  /// 钉住接受的限制之二：非流式响应在读超时时还没提交（Tomcat 缓冲没满），网关清掉半截响应体，
  /// 回一份完整的 ProblemDetail；原因链里没有超时异常，按 500 走。
  @Test
  void should_answer_500_problem_detail_when_body_read_times_out_before_commit() {
    catalog.stubFor(
        get(urlPathEqualTo("/slow-body"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("x".repeat(STREAM_BODY_LENGTH))
                    .withChunkedDribbleDelay(10, 3_000)));

    restClient
        .get()
        .uri("/patra-catalog/slow-body")
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0500")
        .jsonPath("$.status")
        .isEqualTo(500);
  }

  private String gatewayUrl(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
