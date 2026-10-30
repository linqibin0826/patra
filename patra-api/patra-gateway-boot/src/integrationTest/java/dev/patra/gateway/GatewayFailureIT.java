package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;
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
/// 都是带错误码的 ProblemDetail；以及读超时是空闲超时（第 6 节）：数据一直在流就不断。
/// 下游中途停住的情形在 [GatewayStalledDownstreamIT]。
///
/// `test` profile 把读超时压到 1 秒。三条路由各扮一个角色：catalog 指向 WireMock，
/// registry 指向没人监听的端口，interaction 不配实例。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)
class GatewayFailureIT {

  private static final int STREAM_BODY_LENGTH = 10_000;

  /// 24 MB：远大于本机回环上 socket 缓冲与 Tomcat 缓冲之和，网关必须跟着慢速客户端一点点读下游
  private static final int LARGE_BODY_LENGTH = 24 * 1024 * 1024;

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
        .uri("/patra-interaction/libraries")
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

  /// 读超时是空闲超时：流式响应总时长超过读超时，但下游一直在发数据（块间隔 0.3 秒），客户端完整收到。
  @Test
  void should_stream_response_longer_than_read_timeout_while_data_keeps_flowing() throws Exception {
    catalog.stubFor(
        get(urlPathEqualTo("/stream"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody("x".repeat(STREAM_BODY_LENGTH))
                    .withChunkedDribbleDelay(10, 3_000)));

    HttpResponse<String> response = sendDirectly("/patra-catalog/stream");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).hasSize(STREAM_BODY_LENGTH).matches("x+");
  }

  /// 大文件下载（门户的原版 PDF 有几十 MB）经慢速客户端要读很久，远超读超时；只要下游一直有数据可读，
  /// 网关就不能掐断，客户端要逐字节完整收到。下游一次给全，慢的是客户端：网关读下游的节奏被客户端拖慢。
  @Test
  void should_deliver_large_download_completely_to_slow_client_beyond_read_timeout()
      throws Exception {
    byte[] pdf = new byte[LARGE_BODY_LENGTH];
    new Random(42).nextBytes(pdf);
    catalog.stubFor(
        get(urlPathEqualTo("/large.pdf"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/pdf")
                    .withBody(pdf)));

    byte[] received;
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<InputStream> response =
          client.send(
              HttpRequest.newBuilder(URI.create(gatewayUrl("/patra-catalog/large.pdf"))).build(),
              HttpResponse.BodyHandlers.ofInputStream());
      assertThat(response.statusCode()).isEqualTo(200);
      received = readSlowly(response.body());
    }

    assertThat(received.length).isEqualTo(LARGE_BODY_LENGTH);
    assertThat(md5(received)).isEqualTo(md5(pdf));
  }

  /// 模拟慢速客户端：每 10 毫秒读 64 KB，读到流结束或出错为止，返回已收到的字节。
  ///
  /// @param body 响应体流
  /// @return 已收到的全部字节；中途断开时是半截
  private static byte[] readSlowly(InputStream body) throws InterruptedException {
    ByteArrayOutputStream received = new ByteArrayOutputStream();
    byte[] buffer = new byte[64 * 1024];
    try (body) {
      int read;
      while ((read = body.readNBytes(buffer, 0, buffer.length)) > 0) {
        received.write(buffer, 0, read);
        Thread.sleep(10);
      }
    } catch (IOException truncated) {
      // 连接被网关切断：返回已收到的部分，由断言报出长度不符
    }
    return received.toByteArray();
  }

  /// 计算 MD5 的十六进制串，和验收时的 md5 比对同一口径。
  ///
  /// @param bytes 内容
  /// @return 十六进制摘要
  private static String md5(byte[] bytes) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
  }

  /// 用 JDK 客户端直接打网关并把响应体读成字符串；读到半截断开时也照样返回已收到的内容。
  ///
  /// @param path 网关路径
  /// @return 网关的响应
  private HttpResponse<String> sendDirectly(String path) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create(gatewayUrl(path))).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  private String gatewayUrl(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
