package dev.patra.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 受信代理（门户）发来的 `X-Forwarded-For` 被采用（PAP-99 设计第 6.3 节）。测试进程连网关的地址是回环，
/// 这里把两种回环都配成受信代理。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})
@TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=127.0.0.1/32,::1/128")
class GatewayTrustedProxyIT {

  static final String UA_CHROME =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/141.0.0.0 Safari/537.36";

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
    identity.stubFor(post(urlPathEqualTo("/auth/login")).willReturn(okJson("{}")));
  }

  @Test
  void should_forward_client_ip_from_trusted_proxy() {
    LoggedRequest received = login(HttpHeadersBuilder.xff("203.0.113.9"));

    assertThat(received.getHeader("X-Forwarded-For")).isEqualTo("203.0.113.9");
    assertThat(received.getHeader("Forwarded")).contains("for=\"203.0.113.9");
    assertThat(received.getHeader(HttpHeaders.USER_AGENT)).isEqualTo(UA_CHROME);
  }

  @Test
  void should_take_rightmost_untrusted_value_from_trusted_proxy() {
    LoggedRequest received = login(HttpHeadersBuilder.xff("198.51.100.7, 203.0.113.9"));

    assertThat(received.getHeader("X-Forwarded-For")).isEqualTo("203.0.113.9");
  }

  @Test
  void should_fall_back_to_proxy_address_when_trusted_proxy_sends_no_client_ip() {
    LoggedRequest received = login(new HttpHeaders());

    assertThat(received.getHeader("X-Forwarded-For")).matches("127\\.0\\.0\\.1|0:0:0:0:0:0:0:1");
  }

  @Test
  void should_still_strip_prefix_and_forwarded_headers_from_trusted_proxy() {
    HttpHeaders headers = HttpHeadersBuilder.xff("203.0.113.9");
    headers.add("X-Forwarded-Prefix", "/evil");
    headers.add("Forwarded", "for=198.51.100.1;host=evil.example");

    LoggedRequest received = login(headers);

    assertThat(received.getHeader("X-Forwarded-Prefix")).isEqualTo("/patra-identity");
    assertThat(received.getHeader("Forwarded"))
        .doesNotContain("evil")
        .doesNotContain("198.51.100.1");
  }

  /// 以门户的身份登录一次，返回 identity 收到的请求。
  ///
  /// @param headers 额外的请求头
  /// @return identity 收到的请求
  private LoggedRequest login(HttpHeaders headers) {
    restClient
        .post()
        .uri("/patra-identity/auth/login")
        .header(HttpHeaders.USER_AGENT, UA_CHROME)
        .headers(h -> h.addAll(headers))
        .body("{}")
        .exchange()
        .expectStatus()
        .isOk();
    List<LoggedRequest> received =
        identity.findAll(postRequestedFor(urlPathEqualTo("/auth/login")));
    assertThat(received).hasSize(1);
    return received.getFirst();
  }

  /// 测试里拼请求头的小工具。
  static final class HttpHeadersBuilder {

    private HttpHeadersBuilder() {}

    /// 只带 `X-Forwarded-For` 的请求头。
    ///
    /// @param value 头的值
    /// @return 请求头
    static HttpHeaders xff(String value) {
      HttpHeaders headers = new HttpHeaders();
      headers.add("X-Forwarded-For", value);
      return headers;
    }
  }
}
