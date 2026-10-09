package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 迁移前后必须一致的转发行为（设计第 7 节）：剥前缀、查询串、方法与请求体、响应原样、转发头、
/// `Authorization` 透传、不跟随重定向。下游用 WireMock 顶替，`lb://patra-catalog` 经
/// SimpleDiscoveryClient 指到它，LoadBalancer 这条路真的走到。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
class GatewayRoutingIT {

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
  }

  @Test
  void should_strip_prefix_and_forward_query_string() {
    catalog.stubFor(
        get(urlPathEqualTo("/venues"))
            .withQueryParam("page", equalTo("0"))
            .withQueryParam("size", equalTo("20"))
            .willReturn(okJson("{\"items\":[]}").withHeader("X-Downstream", "catalog")));

    restClient
        .get()
        .uri("/patra-catalog/venues?page=0&size=20")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectHeader()
        .valueEquals("X-Downstream", "catalog")
        .expectBody()
        .json("{\"items\":[]}");
  }

  @Test
  void should_keep_encoded_query_string() {
    catalog.stubFor(get(urlPathEqualTo("/publications/search")).willReturn(okJson("{}")));

    // 用 uriBuilder 让客户端按模板编码（空格 -> %20，中文 -> UTF-8 百分号编码），网关必须原样转发
    restClient
        .get()
        .uri(
            builder ->
                builder
                    .path("/patra-catalog/publications/search")
                    .queryParam("q", "GLP-1 肠道")
                    .build())
        .exchange()
        .expectStatus()
        .isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/publications/search"))
            .withQueryParam("q", equalTo("GLP-1 肠道")));
  }

  @Test
  void should_forward_post_body_unchanged() {
    catalog.stubFor(post(urlPathEqualTo("/auth/login")).willReturn(okJson("{\"ok\":true}")));

    restClient
        .post()
        .uri("/patra-catalog/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"email\":\"a@example.com\",\"password\":\"p\"}")
        .exchange()
        .expectStatus()
        .isOk();

    catalog.verify(
        postRequestedFor(urlPathEqualTo("/auth/login"))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/json"))
            .withRequestBody(equalToJson("{\"email\":\"a@example.com\",\"password\":\"p\"}")));
  }

  @Test
  void should_pass_204_and_response_headers_through() {
    catalog.stubFor(
        get(urlPathEqualTo("/venues/1"))
            .willReturn(aResponse().withStatus(204).withHeader("Cache-Control", "no-store")));

    restClient
        .get()
        .uri("/patra-catalog/venues/1")
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectHeader()
        .valueEquals("Cache-Control", "no-store")
        .expectBody()
        .isEmpty();
  }

  @Test
  void should_send_x_forwarded_headers_to_downstream() {
    catalog.stubFor(get(urlPathEqualTo("/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/venues").exchange().expectStatus().isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/venues"))
            .withHeader("X-Forwarded-Host", matching("localhost(:\\d+)?"))
            .withHeader("X-Forwarded-Port", equalTo(gatewayPort()))
            .withHeader("X-Forwarded-Proto", equalTo("http"))
            .withHeader("X-Forwarded-Prefix", equalTo("/patra-catalog"))
            .withHeader("Forwarded", containing("proto=http")));
  }

  @Test
  void should_send_downstream_host_header() {
    catalog.stubFor(get(urlPathEqualTo("/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/venues").exchange().expectStatus().isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/venues"))
            .withHeader("Host", equalTo("localhost:" + catalog.getPort())));
  }

  @Test
  void should_forward_authorization_header_unchanged() {
    catalog.stubFor(get(urlPathEqualTo("/auth/me")).willReturn(okJson("{}")));

    restClient
        .get()
        .uri("/patra-catalog/auth/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer patra_user_opaque")
        .exchange()
        .expectStatus()
        .isOk();

    catalog.verify(
        getRequestedFor(urlPathEqualTo("/auth/me"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer patra_user_opaque")));
  }

  @Test
  void should_pass_downstream_problem_detail_through() {
    String body = "{\"type\":\"about:blank\",\"status\":422,\"code\":\"CATALOG-0422\"}";
    catalog.stubFor(
        get(urlPathEqualTo("/venues/bad"))
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                    .withBody(body)));

    restClient
        .get()
        .uri("/patra-catalog/venues/bad")
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(body);
  }

  @Test
  void should_not_follow_downstream_redirects() throws Exception {
    catalog.stubFor(
        get(urlPathEqualTo("/old"))
            .willReturn(aResponse().withStatus(302).withHeader("Location", "/new")));

    // 测试客户端自己不能跟随重定向，否则看到的是它跟去 /new 后网关给的 404
    HttpResponse<Void> response;
    try (HttpClient client =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      response =
          client.send(
              HttpRequest.newBuilder(URI.create(gatewayUrl("/patra-catalog/old"))).build(),
              HttpResponse.BodyHandlers.discarding());
    }

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location")).contains("/new");
    catalog.verify(0, getRequestedFor(urlPathEqualTo("/new")));
  }

  /// springdoc 3.0 的 webmvc Scalar 控制器映射在 `${scalar.path:/scalar}`，不再是 `/scalar.html`。
  @Test
  void should_serve_scalar_page_itself() {
    restClient
        .get()
        .uri("/scalar")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_HTML);
  }

  /// 网关是代理，不替客户端向下游索要压缩：客户端没发 `Accept-Encoding`，下游就不该收到。
  /// 测试客户端用裸 JDK HttpClient，它自己不会加这个头。
  @Test
  void should_not_negotiate_compression_for_the_client() throws Exception {
    catalog.stubFor(get(urlPathEqualTo("/venues")).willReturn(okJson("{}")));

    HttpResponse<Void> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response =
          client.send(
              HttpRequest.newBuilder(URI.create(gatewayUrl("/patra-catalog/venues"))).build(),
              HttpResponse.BodyHandlers.discarding());
    }

    assertThat(response.statusCode()).isEqualTo(200);
    catalog.verify(
        getRequestedFor(urlPathEqualTo("/venues")).withoutHeader(HttpHeaders.ACCEPT_ENCODING));
  }

  /// 客户端自己要 gzip 时，`Accept-Encoding` 原样到下游，下游的 gzip 响应连头带字节原样回客户端，网关不解压。
  @Test
  void should_pass_gzip_response_through_untouched() throws Exception {
    byte[] gzipped = gzip("{\"items\":[]}");
    catalog.stubFor(
        get(urlPathEqualTo("/venues"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withHeader(HttpHeaders.CONTENT_ENCODING, "gzip")
                    .withBody(gzipped)));

    HttpResponse<byte[]> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response =
          client.send(
              HttpRequest.newBuilder(URI.create(gatewayUrl("/patra-catalog/venues")))
                  .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                  .build(),
              HttpResponse.BodyHandlers.ofByteArray());
    }

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).contains("gzip");
    assertThat(response.body()).isEqualTo(gzipped);
    catalog.verify(
        getRequestedFor(urlPathEqualTo("/venues"))
            .withHeader(HttpHeaders.ACCEPT_ENCODING, equalTo("gzip")));
  }

  private static byte[] gzip(String text) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
      out.write(text.getBytes(StandardCharsets.UTF_8));
    }
    return bytes.toByteArray();
  }

  private String gatewayPort() {
    return environment.getRequiredProperty("local.server.port");
  }

  private String gatewayUrl(String path) {
    return "http://localhost:" + gatewayPort() + path;
  }
}
