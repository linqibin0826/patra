package dev.linqibin.patra.starter.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/// TestGatewayTokenEnvironmentPostProcessor 单元测试。
///
/// 这里只测它对环境做了什么；它是否真的被 Spring Boot 加载，由任务 10、12 的
/// 集成测试验证（没加载的话那些测试的应用会因为缺内部令牌而起不来）。
@DisplayName("TestGatewayTokenEnvironmentPostProcessor 单元测试")
class TestGatewayTokenEnvironmentPostProcessorTest {

  private static final String PROPERTY = "patra.security.gateway-token";

  private final TestGatewayTokenEnvironmentPostProcessor postProcessor =
      new TestGatewayTokenEnvironmentPostProcessor();

  @Test
  @DisplayName("没有配置时补上测试用的内部令牌")
  void should_add_test_token_when_absent() {
    StandardEnvironment environment = new StandardEnvironment();

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(PROPERTY)).isEqualTo(TestIdentity.GATEWAY_TOKEN);
  }

  @Test
  @DisplayName("已有显式配置时不覆盖")
  void should_not_override_explicit_configuration() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("explicit", Map.of(PROPERTY, "explicit-token")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(PROPERTY)).isEqualTo("explicit-token");
  }
}
