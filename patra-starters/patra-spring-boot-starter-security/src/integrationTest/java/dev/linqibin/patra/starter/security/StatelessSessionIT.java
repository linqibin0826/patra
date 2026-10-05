package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.support.SecurityITSessionCounter;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 无会话行为的集成测试：不建会话、不拦 POST、不劫持 `/logout`、不生成默认用户。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("无会话行为 集成测试")
class StatelessSessionIT {

  @Autowired private RestTestClient restClient;
  @Autowired private SecurityITSessionCounter sessionCounter;
  @Autowired private ApplicationContext applicationContext;

  private void getWithoutCookie(String uri, Consumer<HttpHeaders> headers) {
    restClient
        .get()
        .uri(uri)
        .headers(headers)
        .exchange()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE);
  }

  @Test
  @DisplayName("匿名、已登录、401、403、500 的请求都不创建会话，也不下发 Cookie")
  void should_never_create_session_or_set_cookie() {
    getWithoutCookie("/probe/whoami", headers -> {});
    getWithoutCookie("/probe/whoami", headers -> headers.addAll(TestIdentity.headers()));
    getWithoutCookie("/probe/me", headers -> {});
    getWithoutCookie("/probe/access-denied", headers -> headers.addAll(TestIdentity.headers()));
    getWithoutCookie(
        "/probe/whoami",
        headers -> {
          headers.addAll(TestIdentity.headers());
          headers.remove(IdentityHeaders.SESSION_ID);
        });

    assertThat(sessionCounter.createdCount()).isZero();
  }

  @Test
  @DisplayName("POST 请求不被 CSRF 拦截")
  void should_accept_post_without_csrf_token() {
    restClient.post().uri("/probe/notes").exchange().expectStatus().isOk();
  }

  @Test
  @DisplayName("/logout 路径由业务控制器处理，不被框架劫持")
  void should_route_logout_path_to_controller() {
    restClient
        .post()
        .uri("/logout")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("logout handled by controller");
  }

  @Test
  @DisplayName("容器里没有带随机密码的默认用户")
  void should_not_register_default_user() {
    assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
  }
}
