package dev.linqibin.starter.restclient.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.restclient.proxy.TunnelProxyConfigurer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// TunnelProxyAutoConfiguration 单元测试。
///
/// 验证隧道代理自动配置的条件装配逻辑。
@DisplayName("TunnelProxyAutoConfiguration 单元测试")
class TunnelProxyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TunnelProxyAutoConfiguration.class));

  @Test
  @DisplayName("默认情况下不应该创建 TunnelProxyConfigurer（enabled 默认 false）")
  void should_not_create_configurer_by_default() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(TunnelProxyConfigurer.class));
  }

  @Test
  @DisplayName("当 enabled=true 且配置完整时应该创建 TunnelProxyConfigurer")
  void should_create_configurer_when_enabled_with_full_config() {
    contextRunner
        .withPropertyValues(
            "linqibin.starter.rest-client.proxy.tunnel.enabled=true",
            "linqibin.starter.rest-client.proxy.tunnel.host=tunnel.qg.net",
            "linqibin.starter.rest-client.proxy.tunnel.port=15561",
            "linqibin.starter.rest-client.proxy.tunnel.auth-key=testKey",
            "linqibin.starter.rest-client.proxy.tunnel.auth-pwd=testPwd")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TunnelProxyConfigurer.class);
            });
  }

  @Test
  @DisplayName("当 enabled=false 时不应该创建 TunnelProxyConfigurer")
  void should_not_create_configurer_when_disabled() {
    contextRunner
        .withPropertyValues(
            "linqibin.starter.rest-client.proxy.tunnel.enabled=false",
            "linqibin.starter.rest-client.proxy.tunnel.host=tunnel.qg.net",
            "linqibin.starter.rest-client.proxy.tunnel.port=15561",
            "linqibin.starter.rest-client.proxy.tunnel.auth-key=testKey",
            "linqibin.starter.rest-client.proxy.tunnel.auth-pwd=testPwd")
        .run(context -> assertThat(context).doesNotHaveBean(TunnelProxyConfigurer.class));
  }

  @Test
  @DisplayName("当 enabled=true 但缺少必填配置时应该启动失败")
  void should_fail_when_enabled_but_missing_required_config() {
    contextRunner
        .withPropertyValues(
            "linqibin.starter.rest-client.proxy.tunnel.enabled=true",
            "linqibin.starter.rest-client.proxy.tunnel.host=tunnel.qg.net")
        // 缺少 port, auth-key, auth-pwd
        .run(context -> assertThat(context).hasFailed());
  }
}
