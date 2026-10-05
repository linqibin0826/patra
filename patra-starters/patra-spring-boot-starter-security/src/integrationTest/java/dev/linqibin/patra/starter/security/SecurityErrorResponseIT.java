package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 错误输出的集成测试：spec 第 9 节的几条路径输出同一种格式。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("错误输出 集成测试")
class SecurityErrorResponseIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {};

  /// 不带语义特征的异常输出的字段。带特征的领域异常会多一个 `traits`。
  private static final Set<String> BASE_FIELDS =
      Set.of("type", "title", "status", "detail", "instance", "code", "path", "timestamp");

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("需要登录但没登录：401，字段与现有错误格式一致")
  void should_return_401_problem_when_require_and_anonymous() {
    restClient
        .get()
        .uri("/probe/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.detail")
        .isEqualTo("Authentication required")
        .jsonPath("$.instance")
        .isEqualTo("/probe/me")
        .jsonPath("$.traits[0]")
        .isEqualTo("UNAUTHORIZED");
  }

  @Test
  @DisplayName("需要登录且已登录：正常返回当前用户")
  void should_return_user_when_require_and_logged_in() {
    restClient
        .get()
        .uri("/probe/me")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("1001:2001:portal:web");
  }

  @Test
  @DisplayName("业务代码抛带 FORBIDDEN 特征的领域异常：403")
  void should_return_403_problem_when_domain_exception_is_forbidden() {
    restClient
        .get()
        .uri("/probe/forbidden-by-domain")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0403")
        .jsonPath("$.instance")
        .isEqualTo("/probe/forbidden-by-domain");
  }

  @Test
  @DisplayName("控制器里抛出拒绝访问、匿名：401，由安全过滤器输出，日志里不留多余的错误记录")
  void should_return_401_when_controller_throws_access_denied_and_anonymous(CapturedOutput output) {
    restClient
        .get()
        .uri("/probe/access-denied")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.detail")
        .isEqualTo("Authentication required")
        .jsonPath("$.instance")
        .isEqualTo("/probe/access-denied");

    assertThat(output)
        .doesNotContain("Exception handled")
        .doesNotContain("Failure in @ExceptionHandler")
        .doesNotContain("threw exception");
  }

  @Test
  @DisplayName("控制器里抛出拒绝访问、已登录：403，不泄露异常的原始消息")
  void should_return_403_when_controller_throws_access_denied_and_logged_in() {
    restClient
        .get()
        .uri("/probe/access-denied")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0403")
        .jsonPath("$.detail")
        .isEqualTo("Access denied")
        .jsonPath("$.instance")
        .isEqualTo("/probe/access-denied");
  }

  @Test
  @DisplayName("过滤器里输出的和控制器里输出的错误响应，字段集合相同")
  void should_write_same_field_set_in_filter_and_controller() {
    Map<String, Object> fromController =
        restClient
            .get()
            .uri("/probe/boom")
            .exchange()
            .expectStatus()
            .isEqualTo(500)
            .expectBody(JSON_OBJECT)
            .returnResult()
            .getResponseBody();

    Map<String, Object> fromFilter =
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
            .expectBody(JSON_OBJECT)
            .returnResult()
            .getResponseBody();

    assertThat(fromController).containsOnlyKeys(BASE_FIELDS);
    assertThat(fromFilter).containsOnlyKeys(BASE_FIELDS);
  }
}
