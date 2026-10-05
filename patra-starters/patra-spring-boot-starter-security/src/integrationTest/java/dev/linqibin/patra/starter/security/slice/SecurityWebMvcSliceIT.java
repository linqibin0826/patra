package dev.linqibin.patra.starter.security.slice;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.config.SecurityCoreAutoConfiguration;
import dev.linqibin.patra.starter.security.config.SecurityServletAutoConfiguration;
import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.core.json.autoconfig.JacksonAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// 切片测试：安全 starter 在 `@WebMvcTest` 加 `RestTestClient` 里的表现。
///
/// 使用方的切片测试照这个类的配置写：配置根类多导入两个安全自动配置，
/// 测试依赖里加上本模块的 testFixtures。
@WebMvcTest
@AutoConfigureRestTestClient
@Import(SecurityWebMvcSliceIT.SliceProbeController.class)
@DisplayName("安全 starter 切片测试")
class SecurityWebMvcSliceIT {

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("带上 TestIdentity 的请求头：已登录")
  void should_identify_user_when_test_identity_headers_present() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("1001");
  }

  @Test
  @DisplayName("什么头都不带：匿名")
  void should_be_anonymous_when_no_headers() {
    restClient
        .get()
        .uri("/slice/whoami")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("anonymous");
  }

  @Test
  @DisplayName("需要登录但没登录：401，ProblemDetail 格式")
  void should_return_401_problem_when_require_and_anonymous() {
    restClient
        .get()
        .uri("/slice/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0401")
        .jsonPath("$.instance")
        .isEqualTo("/slice/me");
  }

  @Test
  @DisplayName("身份头残缺：500，由安全过滤器输出")
  void should_return_500_problem_when_identity_headers_partial() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.remove(IdentityHeaders.SESSION_ID);
            })
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("TEST-0500")
        .jsonPath("$.instance")
        .isEqualTo("/slice/whoami");
  }

  @Test
  @DisplayName("同一线程上，已登录请求之后的匿名请求看不到上一个用户")
  void should_not_leak_identity_to_next_request_on_same_thread() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(headers -> headers.addAll(TestIdentity.headers()))
        .exchange()
        .expectBody(String.class)
        .isEqualTo("1001");

    restClient
        .get()
        .uri("/slice/whoami")
        .exchange()
        .expectBody(String.class)
        .isEqualTo("anonymous");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("同一线程上，身份头不合法的请求之后，匿名请求仍是匿名")
  void should_stay_anonymous_after_malformed_request_on_same_thread() {
    restClient
        .get()
        .uri("/slice/whoami")
        .headers(
            headers -> {
              headers.addAll(TestIdentity.headers());
              headers.set(IdentityHeaders.USER_ID, "abc");
            })
        .exchange()
        .expectStatus()
        .isEqualTo(500);

    restClient
        .get()
        .uri("/slice/whoami")
        .exchange()
        .expectBody(String.class)
        .isEqualTo("anonymous");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  /// 切片测试的配置根类：在现有的三个自动配置之外，多导入两个安全自动配置。
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ImportAutoConfiguration({
    CoreErrorAutoConfiguration.class,
    WebErrorAutoConfiguration.class,
    JacksonAutoConfiguration.class,
    SecurityCoreAutoConfiguration.class,
    SecurityServletAutoConfiguration.class
  })
  static class SliceConfig {}

  /// 切片测试专用的探针接口。
  @RestController
  static class SliceProbeController {

    private final CurrentUserPort currentUserPort;

    /// 创建探针控制器。
    ///
    /// @param currentUserPort 取当前用户的端口
    SliceProbeController(CurrentUserPort currentUserPort) {
      this.currentUserPort = currentUserPort;
    }

    /// 描述当前是谁。
    ///
    /// @return 匿名时是 `anonymous`，已登录时是用户 ID
    @GetMapping("/slice/whoami")
    String whoAmI() {
      return currentUserPort
          .current()
          .map(user -> Long.toString(user.userId()))
          .orElse("anonymous");
    }

    /// 需要登录的接口。
    ///
    /// @return 当前用户的 ID
    @GetMapping("/slice/me")
    String me() {
      return Long.toString(currentUserPort.require().userId());
    }
  }
}
