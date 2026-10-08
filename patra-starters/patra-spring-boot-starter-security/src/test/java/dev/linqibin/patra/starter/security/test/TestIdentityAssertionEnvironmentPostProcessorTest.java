package dev.linqibin.patra.starter.security.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/// TestIdentityAssertionEnvironmentPostProcessor 单元测试。
///
/// 这里只测它对环境做了什么；它是否真的被 Spring Boot 加载，由集成测试验证
/// （没加载的话那些测试的应用会因为缺公钥而起不来）。
@DisplayName("TestIdentityAssertionEnvironmentPostProcessor 单元测试")
class TestIdentityAssertionEnvironmentPostProcessorTest {

  private static final String PROPERTY = "patra.security.identity-assertion.public-keys";

  private final TestIdentityAssertionEnvironmentPostProcessor postProcessor =
      new TestIdentityAssertionEnvironmentPostProcessor();

  @Test
  @DisplayName("没有配置时补上测试公钥")
  void should_add_test_public_keys_when_absent() {
    StandardEnvironment environment = new StandardEnvironment();

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(PROPERTY)).isEqualTo(TestSigningKey.publicJwkSet());
  }

  @Test
  @DisplayName("已有显式配置时不覆盖")
  void should_not_override_explicit_configuration() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("explicit", Map.of(PROPERTY, "{\"keys\":[]}")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty(PROPERTY)).isEqualTo("{\"keys\":[]}");
  }
}
