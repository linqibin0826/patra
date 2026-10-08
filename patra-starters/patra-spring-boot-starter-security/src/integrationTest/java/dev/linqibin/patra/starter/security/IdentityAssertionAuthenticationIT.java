package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.support.SecurityITAssertions;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 从身份断言建立认证的集成测试：spec 第 8.2 节的四种情况。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("从身份断言建立认证 集成测试")
class IdentityAssertionAuthenticationIT {

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("有效的断言：已登录，四个字段都对，且不被重定向")
  void should_identify_user_when_assertion_valid() {
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
        .isEqualTo("1001:2001:user:web");
  }

  @Test
  @DisplayName("没有 Authorization 头：匿名")
  void should_be_anonymous_when_no_assertion() {
    restClient
        .get()
        .uri("/probe/whoami")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("不是 JWT：401，ProblemDetail 格式，带 WWW-Authenticate，日志记原因不记内容")
  void should_reject_with_401_problem_when_token_is_garbage(CapturedOutput output) {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(headers -> headers.setBearerAuth(SecurityITAssertions.garbage()))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.title")
        .isEqualTo("TEST-0401")
        .jsonPath("$.detail")
        .isEqualTo("Authentication required")
        .jsonPath("$.instance")
        .isEqualTo("/probe/whoami")
        .jsonPath("$.path")
        .isEqualTo("/probe/whoami")
        .jsonPath("$.type")
        .exists()
        .jsonPath("$.timestamp")
        .exists();

    assertThat(output).contains("拒绝身份断言").doesNotContain("not-a-jwt");
  }

  @Test
  @DisplayName("过期的断言：401，日志里没有断言的内容")
  void should_reject_with_401_when_assertion_expired(CapturedOutput output) {
    String expired = SecurityITAssertions.expired();

    restClient
        .get()
        .uri("/probe/whoami")
        .headers(headers -> headers.setBearerAuth(expired))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401");

    assertThat(output).contains("拒绝身份断言").doesNotContain(expired);
  }

  @Test
  @DisplayName("Authorization 头出现两次：401")
  void should_reject_with_401_when_authorization_header_repeated() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(
            headers -> {
              headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + TestIdentity.assertion());
              headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + TestIdentity.assertion());
            })
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401");
  }

  @Test
  @DisplayName("签名正确但内容不合法：500，ProblemDetail 格式")
  void should_reject_with_500_problem_when_claims_malformed() {
    restClient
        .get()
        .uri("/probe/whoami")
        .headers(headers -> headers.setBearerAuth(SecurityITAssertions.malformed()))
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
}
