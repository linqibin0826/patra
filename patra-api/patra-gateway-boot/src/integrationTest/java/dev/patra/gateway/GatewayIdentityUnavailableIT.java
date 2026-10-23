package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// identity 查不了会话（PAP-106 设计第 6 节）：带令牌的请求不管打哪条路由都是 503，不是 401 也不是 500，
/// 且不转给下游；不带令牌的请求不查会话，照常放行。
///
/// 两种情形：identity 回 503 + `DEP_UNAVAILABLE`（它那边 Redis 暂时不可用）；连接被重置（连不上）。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)
class GatewayIdentityUnavailableIT {

  private static final String TOKEN = "patra_user_Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";

  @RegisterExtension
  static final WireMockExtension identity =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @Autowired private RestTestClient restClient;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
  }

  @BeforeEach
  void resetDownstreams() {
    identity.resetAll();
    catalog.resetAll();
  }

  @Test
  void should_answer_503_when_identity_reports_its_session_store_unavailable() {
    identity.stubFor(
        post(urlPathEqualTo(GatewayITSessions.INTROSPECT_PATH))
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .withBody(
                        "{\"title\":\"Service Unavailable\",\"status\":503,"
                            + "\"code\":\"IDN-0503\",\"traits\":[\"DEP_UNAVAILABLE\"]}")));

    expectUnavailableOnEveryRoute();
  }

  @Test
  void should_answer_503_when_identity_cannot_be_reached() {
    identity.stubFor(
        post(urlPathEqualTo(GatewayITSessions.INTROSPECT_PATH))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    expectUnavailableOnEveryRoute();
  }

  @Test
  void should_not_look_up_sessions_for_requests_without_a_token() {
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/portal/venues").exchange().expectStatus().isOk();

    identity.verify(0, anyRequestedFor(anyUrl()));
  }

  private void expectUnavailableOnEveryRoute() {
    expectUnavailable(HttpMethod.GET, "/patra-identity/auth/me");
    expectUnavailable(HttpMethod.GET, "/patra-catalog/portal/venues");
    expectUnavailable(HttpMethod.POST, "/patra-identity/auth/logout");

    identity.verify(0, anyRequestedFor(urlPathEqualTo("/auth/me")));
    identity.verify(0, anyRequestedFor(urlPathEqualTo("/auth/logout")));
    catalog.verify(0, anyRequestedFor(anyUrl()));
  }

  private void expectUnavailable(HttpMethod method, String path) {
    restClient
        .method(method)
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0503")
        .jsonPath("$.detail")
        .isEqualTo("Service Unavailable");
  }
}
