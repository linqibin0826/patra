package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
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
class GatewayFailureIT {

  private static final int STREAM_BODY_LENGTH = 10_000;

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
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
        .isEqualTo("GW-0503");
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
        .isEqualTo("GW-0503");
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
        .isEqualTo("GW-0504");
  }

  /// 钉住接受的限制：读超时从请求发出起算、持续有数据也不重置，响应体超过 1 秒还没发完就被切断。
  /// 此时状态码已经发出，客户端拿到的是半截响应或连接异常，不会是 504。框架行为变了这条会先知道。
  @Test
  void should_cut_streaming_response_at_read_timeout() throws Exception {
    catalog.stubFor(
        get(urlPathEqualTo("/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("x".repeat(STREAM_BODY_LENGTH))
                    .withChunkedDribbleDelay(10, 3_000)));

    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getRequiredProperty("local.server.port")
                        + "/patra-catalog/stream"))
            .build();
    int received;
    try (HttpClient client = HttpClient.newHttpClient()) {
      received = client.send(request, HttpResponse.BodyHandlers.ofString()).body().length();
    } catch (IOException truncated) {
      received = -1;
    }

    assertThat(received)
        .as("响应体应被读超时切断：要么读到一半抛 IOException，要么长度不足 %d", STREAM_BODY_LENGTH)
        .isLessThan(STREAM_BODY_LENGTH);
  }
}
