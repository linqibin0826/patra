package dev.linqibin.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.session.SessionToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.security.SecureRandom;
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

/// Redis 连不上（设计第 7.1、11 节）：带令牌的请求不管打哪条路由都是 503，不是 401 也不是 500；
/// 不带令牌的请求不碰 Redis，照常放行。
///
/// Redis 指向一个没人监听的端口，不停共享的 Redis 测试容器，所以不挂 `RedisContainerInitializer`。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)
class GatewayRedisUnavailableIT {

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
  static void unreachableRedisAndDownstreams(DynamicPropertyRegistry registry) {
    int closedPort = closedPort();
    registry.add("spring.data.redis.url", () -> "redis://127.0.0.1:" + closedPort);
    registry.add("spring.data.redis.connect-timeout", () -> "500ms");
    registry.add("spring.data.redis.timeout", () -> "500ms");
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
  }

  @Test
  void should_answer_503_for_any_route_when_a_token_is_present() {
    String token = SessionToken.generate(AccountType.USER, new SecureRandom()).value();

    expectUnavailable(HttpMethod.GET, "/patra-identity/auth/me", token);
    expectUnavailable(HttpMethod.GET, "/patra-catalog/portal/venues", token);
    expectUnavailable(HttpMethod.POST, "/patra-identity/auth/logout", token);

    identity.verify(0, anyRequestedFor(anyUrl()));
    catalog.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void should_not_touch_redis_for_requests_without_a_token() {
    catalog.stubFor(get(urlPathEqualTo("/portal/venues")).willReturn(okJson("{}")));

    restClient.get().uri("/patra-catalog/portal/venues").exchange().expectStatus().isOk();
  }

  private void expectUnavailable(HttpMethod method, String path, String token) {
    restClient
        .method(method)
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
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

  /// 拿一个刚释放、没人监听的端口。
  ///
  /// @return 端口
  private static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
