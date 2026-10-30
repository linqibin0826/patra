package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 拒绝名单（设计第 6.1 节）：`/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` 对任何人 403、
/// 下游收不到请求；网关自己的 actuator 不受影响；畸形路径由框架防火墙 400。
///
/// registry 这条路由故意不配实例：要是规则没拦住，得到的是 503 而不是 403，一样能分辨。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)
class GatewayBlockedPathsIT {

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true).gzipDisabled(true))
          .build();

  @RegisterExtension
  static final WireMockExtension identity =
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
        "spring.cloud.discovery.client.simple.instances.patra-identity[0].uri", identity::baseUrl);
  }

  @BeforeEach
  void resetDownstreams() {
    catalog.resetAll();
    identity.resetAll();
  }

  @Test
  void should_answer_403_for_blocked_paths_whether_anonymous_or_logged_in() {
    String token = GatewayITSessions.issue(identity, 4301L, 9301L);
    List<String> blocked =
        List.of(
            "/patra-registry/_internal/dictionaries/resolve",
            "/patra-catalog/_internal/organizations/04jztag35",
            "/patra-catalog/_internal/mesh/descriptors/D003920",
            "/patra-catalog/_internal/publications/resolve/by-ids",
            "/patra-catalog/_internal/venues/resolve/by-ids",
            "/patra-catalog/_internal/venues/resolve/by-nlm-ids",
            "/patra-identity/_internal/sessions/introspect",
            "/patra-identity/_internal/users/suggest",
            "/patra-identity/_internal/users/resolve/by-usernames",
            "/patra-identity/admin/users/1/ban",
            "/patra-catalog/actuator/health",
            "/patra-identity/actuator/env");

    for (String path : blocked) {
      expectForbidden(restClient.get().uri(path));
      expectForbidden(
          restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
    expectForbidden(
        restClient
            .post()
            .uri("/patra-identity/admin/users/1/ban")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    catalog.verify(0, anyRequestedFor(anyUrl()));
    identity.verify(0, anyRequestedFor(anyUrl()));
  }

  /// 内部接口多是 POST 带 JSON 体（PAP-109 的文献解析、PAP-117 的期刊解析），拒绝名单对 POST 同样生效。
  @Test
  void should_block_internal_post_with_json_body() {
    String token = GatewayITSessions.issue(identity, 4302L, 9302L);
    Map<String, String> internalPosts =
        Map.of(
            "/patra-catalog/_internal/publications/resolve/by-ids", "{\"ids\":[1]}",
            "/patra-catalog/_internal/venues/resolve/by-ids", "{\"ids\":[1]}",
            "/patra-catalog/_internal/venues/resolve/by-nlm-ids", "{\"nlmIds\":[\"0404401\"]}");

    internalPosts.forEach(
        (path, body) -> {
          expectForbidden(
              restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body));
          expectForbidden(
              restClient
                  .post()
                  .uri(path)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(body));
        });

    catalog.verify(0, anyRequestedFor(anyUrl()));
  }

  /// 匹配的是解码后的路径：`%5F` 就是 `_`；`/**` 也匹配零个尾段。
  @Test
  void should_block_encoded_and_bare_internal_paths() {
    for (String path : List.of("/patra-catalog/_internal", "/patra-catalog/%5Finternal/x")) {
      // 用绝对 URI 绕过客户端的模板编码，百分号才能原样发出去
      expectForbidden(restClient.get().uri(URI.create(gatewayUrl(path))));
    }

    catalog.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void should_keep_the_gateway_own_actuator_reachable() {
    restClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  /// 双斜杠和编码过的点由 Spring Security 的 `StrictHttpFirewall` 拒绝，到不了规则和路由。
  @Test
  void should_let_the_firewall_reject_abnormal_paths() {
    for (String path :
        List.of("/patra-catalog//_internal/x", "/patra-catalog/%2e%2e/_internal/x")) {
      restClient.get().uri(URI.create(gatewayUrl(path))).exchange().expectStatus().isBadRequest();
    }

    catalog.verify(0, anyRequestedFor(anyUrl()));
  }

  private static void expectForbidden(RestTestClient.RequestHeadersSpec<?> request) {
    request
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectHeader()
        .doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0403")
        .jsonPath("$.detail")
        .isEqualTo("Access denied");
  }

  private String gatewayUrl(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
