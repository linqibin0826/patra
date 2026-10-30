package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
}
