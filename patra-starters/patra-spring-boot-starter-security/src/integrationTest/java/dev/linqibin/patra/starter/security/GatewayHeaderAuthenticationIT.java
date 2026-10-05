package dev.linqibin.patra.starter.security;

import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 从网关请求头建立认证的集成测试：spec 第 8.2 节的四种情况。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("从网关请求头建立认证 集成测试")
class GatewayHeaderAuthenticationIT {

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("内部令牌正确、身份头齐全：已登录，四个字段都对，且不被重定向")
  void should_identify_user_when_token_and_identity_headers_valid() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .doesNotExist(HttpHeaders.LOCATION)
        .expectBody(String.class)
        .isEqualTo("1001:2001:portal:web");
  }

  @Test
  @DisplayName("内部令牌正确、没有身份头：匿名")
  void should_be_anonymous_when_token_valid_and_no_identity_headers() {
    restClient
        .get()
        .uri("/probe/whoami")
        .header(IdentityHeaders.GATEWAY_TOKEN, TestIdentity.GATEWAY_TOKEN)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("内部令牌缺失：身份头不被采信，按匿名处理")
  void should_be_anonymous_when_token_missing() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.remove(IdentityHeaders.GATEWAY_TOKEN);
            })
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("内部令牌不对：身份头不被采信，按匿名处理")
  void should_be_anonymous_when_token_wrong() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.set(IdentityHeaders.GATEWAY_TOKEN, "wrong-token");
            })
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("内部令牌正确、身份头残缺：500，ProblemDetail 格式")
  void should_reject_with_500_problem_when_identity_headers_partial() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.remove(IdentityHeaders.SESSION_ID);
            })
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(500)
        .jsonPath("$.code")
        .isEqualTo("TEST-0500")
        .jsonPath("$.title")
        .isEqualTo("TEST-0500")
        .jsonPath("$.detail")
        .isEqualTo("Internal Server Error")
        .jsonPath("$.instance")
        .isEqualTo("/probe/whoami")
        .jsonPath("$.path")
        .isEqualTo("/probe/whoami")
        .jsonPath("$.type")
        .exists()
        .jsonPath("$.timestamp")
        .exists();
  }

  @Test
  @DisplayName("内部令牌正确、用户 ID 不是正整数：500")
  void should_reject_with_500_problem_when_user_id_invalid() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.set(IdentityHeaders.USER_ID, "abc");
            })
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0500");
  }
}
