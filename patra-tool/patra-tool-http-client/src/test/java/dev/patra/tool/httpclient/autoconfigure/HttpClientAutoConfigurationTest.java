package dev.patra.tool.httpclient.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.httpclient.interceptor.LoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class HttpClientAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class));

  @Test
  void should_register_default_and_long_running_clients() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasBean("defaultRestClient")
                .hasBean("longRunningRestClient")
                .hasSingleBean(LoggingInterceptor.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.http-client.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(RestClient.class));
  }

  @Test
  void should_back_off_when_user_defines_default_client() {
    RestClient custom = RestClient.create();
    runner
        .withBean("defaultRestClient", RestClient.class, () -> custom)
        .run(ctx -> assertThat(ctx.getBean("defaultRestClient")).isSameAs(custom));
  }

  @Test
  void should_bind_timeouts_and_clients() {
    runner
        .withPropertyValues(
            "patra.http-client.rest.timeout.read=3s",
            "patra.http-client.rest.clients.long-running.enabled=false")
        .run(
            ctx -> {
              assertThat(ctx.getBean(HttpClientProperties.class).rest().timeout().read())
                  .hasSeconds(3);
              assertThat(ctx).doesNotHaveBean("longRunningRestClient");
            });
  }
}
