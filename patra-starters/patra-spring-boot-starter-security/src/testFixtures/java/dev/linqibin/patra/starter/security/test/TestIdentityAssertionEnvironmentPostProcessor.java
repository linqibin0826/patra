package dev.linqibin.patra.starter.security.test;

import dev.linqibin.patra.starter.security.config.PatraSecurityProperties.IdentityAssertion;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/// 测试里自动把 `patra.security.identity-assertion.public-keys` 设成测试公钥。
///
/// 只要测试 classpath 上有本模块的 testFixtures 就生效。放在最低优先级，
/// 测试里显式配置的值会覆盖它。
public class TestIdentityAssertionEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String PROPERTY_SOURCE_NAME = "patraSecurityTestIdentityAssertion";

  /// 往环境里追加一个只含测试公钥的属性源。
  ///
  /// @param environment 应用的环境
  /// @param application 当前的 Spring 应用
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of(IdentityAssertion.PUBLIC_KEYS_PROPERTY, TestSigningKey.publicJwkSet())));
  }
}
