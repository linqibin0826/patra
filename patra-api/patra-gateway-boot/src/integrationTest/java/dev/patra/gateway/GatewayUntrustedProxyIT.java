package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 默认配置谁都不信：别人带来的 `X-Forwarded-For` 被剥掉，出站写的是连接本身的地址（PAP-99 设计第 6.3 节）；
/// 登录设备接口在需登录规则之下（第 15 节）。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)
class GatewayUntrustedProxyIT {

  private static final String BEARER = "Bearer ";

  @RegisterExtension
  static final WireMockExtension identity =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @Autowired private RestTestClient restClient;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
  }

  @BeforeEach
  void resetDownstream() {
    identity.resetAll();
  }

  @Test
  void should_ignore_client_ip_from_untrusted_source() {
    identity.stubFor(post(urlPathEqualTo("/auth/login")).willReturn(okJson("{}")));

    restClient
        .post()
        .uri("/patra-identity/auth/login")
        .header(HttpHeaders.USER_AGENT, GatewayTrustedProxyIT.UA_CHROME)
        .header("X-Forwarded-For", "203.0.113.9")
        .body("{}")
        .exchange()
        .expectStatus()
        .isOk();

    List<LoggedRequest> received =
        identity.findAll(postRequestedFor(urlPathEqualTo("/auth/login")));
    assertThat(received).hasSize(1);
    LoggedRequest request = received.getFirst();
    assertThat(request.getHeader("X-Forwarded-For"))
        .doesNotContain("203.0.113.9")
        .matches("127\\.0\\.0\\.1|0:0:0:0:0:0:0:1");
    assertThat(request.getHeader("Forwarded")).doesNotContain("203.0.113.9");
    assertThat(request.getHeader(HttpHeaders.USER_AGENT))
        .isEqualTo(GatewayTrustedProxyIT.UA_CHROME);
  }

  @Test
  void should_require_session_for_me_sessions() {
    restClient.get().uri("/patra-identity/me/sessions").exchange().expectStatus().isUnauthorized();
    restClient
        .delete()
        .uri("/patra-identity/me/sessions/others")
        .exchange()
        .expectStatus()
        .isUnauthorized();

    identity.stubFor(get(urlPathEqualTo("/me/sessions")).willReturn(okJson("{}")));
    identity.stubFor(delete(urlPathEqualTo("/me/sessions/others")).willReturn(okJson("{}")));
    String token = GatewayITSessions.issue(identity, 4291L, 9291L);

    restClient
        .get()
        .uri("/patra-identity/me/sessions")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();
    restClient
        .delete()
        .uri("/patra-identity/me/sessions/others")
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .exchange()
        .expectStatus()
        .isOk();

    LoggedRequest received =
        identity.findAll(getRequestedFor(urlPathEqualTo("/me/sessions"))).getFirst();
    assertThat(received.getHeader(HttpHeaders.AUTHORIZATION))
        .startsWith(BEARER)
        .doesNotContain(token);
  }
}
