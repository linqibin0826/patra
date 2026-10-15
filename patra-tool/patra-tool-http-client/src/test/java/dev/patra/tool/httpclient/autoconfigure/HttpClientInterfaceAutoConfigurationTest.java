package dev.patra.tool.httpclient.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.httpclient.factory.RestClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HttpClientInterfaceAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class, HttpClientInterfaceAutoConfiguration.class));

  @Test
  void should_register_builder_and_factory_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasBean("httpInterfaceRestClientBuilder")
                .hasSingleBean(RestClientFactory.class));
  }

  @Test
  void should_register_nothing_when_switched_off() {
    runner
        .withPropertyValues("patra.http-client.http-interface.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(RestClientFactory.class));
  }

  @Test
  void should_bind_connect_timeout() {
    runner
        .withPropertyValues("patra.http-client.http-interface.connect-timeout=2s")
        .run(
            ctx ->
                assertThat(ctx.getBean(HttpClientProperties.class).httpInterface().connectTimeout())
                    .hasSeconds(2));
  }
}
