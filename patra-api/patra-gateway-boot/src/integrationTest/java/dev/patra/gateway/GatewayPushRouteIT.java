package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 推送路由（PAP-145，设计 `docs/patra/specs/2026-10-31-notification-realtime-push-design.md` 第 7 节）：
/// 推送路径要登录、走独立连接池、满了立即 503 而普通请求照常。
///
/// identity 与 notification 都由 WireMock 顶替；推送连接池上限压到 2，占满它只要两条连接。
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "patra.gateway.push.max-connections=2")
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)
class GatewayPushRouteIT {

  private static final String BEARER = "Bearer ";

  /// 推送路径（剥前缀之前）。
  private static final String STREAM = "/patra-notification/me/notifications/stream";

  /// 推送路径在 notification 上的样子（剥掉第一段）。
  private static final String DOWNSTREAM_STREAM = "/me/notifications/stream";

  private static final long USER_ID = 7L;

  @RegisterExtension
  static final WireMockExtension identity =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @RegisterExtension
  static final WireMockExtension notification =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @Autowired private RestTestClient restClient;
  @Autowired private Environment environment;

  @Autowired
  @Qualifier("identityAssertionDecoder")
  private JwtDecoder decoder;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-notification[0].uri",
        notification::baseUrl);
  }

  @BeforeEach
  void resetDownstreams() {
    identity.resetAll();
    notification.resetAll();
    GatewayITSessions.inactiveByDefault(identity);
  }

  /// 没登录的推送请求在网关就被拒绝，不借推送池的连接，也到不了 notification。
  @Test
  void should_reject_anonymous_stream_request_at_gateway() {
    restClient
        .get()
        .uri(STREAM)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0401");

    notification.verify(0, anyRequestedFor(anyUrl()));
  }

  /// 登出（会话失效）后，浏览器到点重连时被网关以 401 拒绝。
  @Test
  void should_reject_stream_reconnect_after_logout() {
    notification.stubFor(
        get(urlPathEqualTo(DOWNSTREAM_STREAM))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .withBody(":ping\n\n")));
    String token = GatewayITSessions.issue(identity, USER_ID, 1L);

    restClient
        .get()
        .uri(STREAM)
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isOk();

    GatewayITSessions.revoke(identity, token);

    restClient
        .get()
        .uri(STREAM)
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0401");

    notification.verify(1, getRequestedFor(urlPathEqualTo(DOWNSTREAM_STREAM)));
  }

  /// 推送路由和普通路由一样处理出站头：下游收到剥了前缀的路径与网关现签的身份断言，收不到浏览器带的
  /// 会话令牌；事件流原样透传。守着「推送处理器收到容器刷新事件、收集了出站头过滤器」这件事。
  @Test
  void should_forward_stream_with_assertion_and_without_session_token() {
    String event = "event: unread-count\ndata: {\"total\":1}\n\n";
    notification.stubFor(
        get(urlPathEqualTo(DOWNSTREAM_STREAM))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .withBody(event)));
    String token = GatewayITSessions.issue(identity, USER_ID, 1L);

    restClient
        .get()
        .uri(STREAM)
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo(event);

    List<LoggedRequest> forwarded =
        notification.findAll(getRequestedFor(urlPathEqualTo(DOWNSTREAM_STREAM)));
    assertThat(forwarded).hasSize(1);
    String authorization = forwarded.getFirst().getHeader(HttpHeaders.AUTHORIZATION);
    assertThat(authorization).startsWith(BEARER).doesNotContain(token);
    Jwt assertion = decoder.decode(authorization.substring(BEARER.length()));
    assertThat(assertion.getSubject()).isEqualTo(String.valueOf(USER_ID));
  }

  /// 推送连接占满专用池后：多出来的推送立即 503（不排队），经网关的普通请求照常 200（AC：连接池隔离）。
  ///
  /// 下游的推送桩每 400 毫秒滴一块数据、共 2 秒，短于测试读超时 1 秒的间隔，连接一直占着。
  @Test
  void should_reject_extra_stream_at_once_when_push_pool_is_full_while_other_requests_pass()
      throws Exception {
    notification.stubFor(
        get(urlPathEqualTo(DOWNSTREAM_STREAM))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .withBody(":ping\n\n".repeat(5))
                    .withChunkedDribbleDelay(5, 2_000)));
    notification.stubFor(
        get(urlPathEqualTo("/me/notifications/unread-count"))
            .willReturn(okJson("{\"total\":0,\"replies\":0,\"mentions\":0,\"helpful\":0}")));
    String token = GatewayITSessions.issue(identity, USER_ID, 1L);

    try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
      List<HttpResponse<InputStream>> open =
          List.of(openStream(client, token), openStream(client, token)).stream()
              .map(CompletableFuture::join)
              .toList();
      try {
        assertThat(open).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));

        long started = System.nanoTime();
        HttpResponse<String> extra =
            client.send(streamRequest(token), HttpResponse.BodyHandlers.ofString());
        Duration waited = Duration.ofNanos(System.nanoTime() - started);

        assertThat(extra.statusCode()).isEqualTo(503);
        assertThat(extra.body()).contains("\"code\":\"GW-0503\"");
        assertThat(waited).isLessThan(Duration.ofSeconds(1));

        restClient
            .get()
            .uri("/patra-notification/me/notifications/unread-count")
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .exchange()
            .expectStatus()
            .isOk();

        // 读完两条流：连接还回池子，后面的用例不会撞上满池
        for (HttpResponse<InputStream> response : open) {
          response.body().readAllBytes();
        }
      } finally {
        for (HttpResponse<InputStream> response : open) {
          response.body().close();
        }
      }
    }
  }

  /// 每条事件一到就转给浏览器，不等攒够缓冲区、也不等下游结束。
  ///
  /// 下游每 600 毫秒滴一条 7 字节的心跳、共 6 秒；第一条要在 3 秒内（一半）到达客户端，留出首个请求的
  /// 预热（负载均衡子上下文、查会话客户端）。Reactor 客户端的响应体
  /// 输入流（Spring 7.0.10 的 `SubscriberInputStream`）一次读要读满缓冲区才返回，推送路由不能照搬
  /// Gateway 的「读一块、刷一次」。
  @Test
  void should_deliver_each_event_as_soon_as_it_arrives() throws Exception {
    notification.stubFor(
        get(urlPathEqualTo(DOWNSTREAM_STREAM))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .withBody(":ping\n\n".repeat(10))
                    .withChunkedDribbleDelay(10, 6_000)));
    String token = GatewayITSessions.issue(identity, USER_ID, 1L);

    try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
      long started = System.nanoTime();
      HttpResponse<InputStream> response =
          client.send(streamRequest(token), HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream body = response.body()) {
        byte[] firstEvent = body.readNBytes(":ping\n\n".length());
        Duration waited = Duration.ofNanos(System.nanoTime() - started);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(firstEvent, StandardCharsets.UTF_8)).isEqualTo(":ping\n\n");
        assertThat(waited).isLessThan(Duration.ofSeconds(3));
        body.readAllBytes();
      }
    }
  }

  /// 异步开一条推送连接，拿到响应头即完成。
  ///
  /// @param client JDK HTTP 客户端（HTTP/1.1，每条请求一条连接）
  /// @param token 会话令牌
  /// @return 拿到响应头时完成的响应，响应体是还在流的输入流
  private CompletableFuture<HttpResponse<InputStream>> openStream(HttpClient client, String token) {
    return client.sendAsync(streamRequest(token), HttpResponse.BodyHandlers.ofInputStream());
  }

  /// 经网关的推送请求。
  ///
  /// @param token 会话令牌
  /// @return 请求
  private HttpRequest streamRequest(String token) {
    return HttpRequest.newBuilder(URI.create(gatewayUrl(STREAM)))
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
        .build();
  }

  /// 网关上某个路径的完整地址。
  ///
  /// @param path 路径
  /// @return 地址
  private String gatewayUrl(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
