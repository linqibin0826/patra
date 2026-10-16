package dev.patra.tool.httpclient.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.httpclient.proxy.TunnelProxyConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HttpClientProxyAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(HttpClientProxyAutoConfiguration.class));

  @Test
  void should_not_register_configurer_by_default() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(TunnelProxyConfigurer.class));
  }

  @Test
  void should_register_configurer_when_tunnel_enabled_with_credentials() {
    runner
        .withPropertyValues(
            "patra.http-client.proxy.tunnel.enabled=true",
            "patra.http-client.proxy.tunnel.host=proxy.example",
            "patra.http-client.proxy.tunnel.port=15057",
            "patra.http-client.proxy.tunnel.auth-key=k",
            "patra.http-client.proxy.tunnel.auth-pwd=p")
        .run(ctx -> assertThat(ctx).hasSingleBean(TunnelProxyConfigurer.class));
  }

  @Test
  void should_skip_configurer_when_module_disabled() {
    runner
        .withPropertyValues(
            "patra.http-client.enabled=false", "patra.http-client.proxy.tunnel.enabled=true")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(TunnelProxyConfigurer.class));
  }
}
