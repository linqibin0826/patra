package dev.linqibin.patra.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.starter.httpinterface.factory.RestClientFactory;
import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestClient;

/// 内部客户端转发断言的集成测试：spec 第 10 节。
///
/// 测试应用用 `RestClientFactory` 建一个指向自己的内部客户端，在当前线程放一个带断言的用户，
/// 再经它调自己的探针接口，看对方认出了谁。
@SpringBootTest(
    classes = SecurityITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("内部客户端转发断言 集成测试")
class IdentityAssertionForwardingIT {

  @Autowired private Environment environment;
  @Autowired private RestClientFactory factory;

  @Autowired
  @Qualifier("httpInterfaceRestClientBuilder")
  private RestClient.Builder builder;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("当前线程有带断言的用户：经内部客户端调过去，对方认出同一个用户")
  void should_forward_assertion_through_internal_client() {
    login();

    String whoami = internalClient().get().uri("/probe/whoami").retrieve().body(String.class);

    assertThat(whoami).isEqualTo("1001:2001:user:web");
  }

  @Test
  @DisplayName("当前线程没有用户：对方是匿名")
  void should_call_anonymously_without_current_user() {
    String whoami = internalClient().get().uri("/probe/whoami").retrieve().body(String.class);

    assertThat(whoami).isEqualTo("anonymous");
  }

  @Test
  @DisplayName("不经 RestClientFactory 建的客户端没有拦截器：对方是匿名")
  void should_not_forward_through_plain_builder() {
    login();
    RestClient plain = builder.clone().baseUrl(baseUrl()).build();

    String whoami = plain.get().uri("/probe/whoami").retrieve().body(String.class);

    assertThat(whoami).isEqualTo("anonymous");
  }

  /// 用工厂建的、指向本应用的内部客户端。
  private RestClient internalClient() {
    return factory.createRestClient(builder, "self", baseUrl());
  }

  /// 本应用的地址，端口是随机的。
  private String baseUrl() {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port");
  }

  /// 在当前线程放一个带断言的用户，模拟验签通过后的请求线程。
  private static void login() {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new CurrentUserAuthentication(TestIdentity.user(), TestIdentity.assertion()));
    SecurityContextHolder.setContext(context);
  }
}
