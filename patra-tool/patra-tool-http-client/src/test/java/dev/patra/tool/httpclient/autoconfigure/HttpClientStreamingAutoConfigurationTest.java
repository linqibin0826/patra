package dev.patra.tool.httpclient.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

class HttpClientStreamingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(HttpClientStreamingAutoConfiguration.class));

  @Test
  void should_register_streaming_web_client_by_default() {
    runner.run(ctx -> assertThat(ctx).hasBean("streamingWebClient"));
  }

  @Test
  void should_skip_streaming_client_without_webflux_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(WebClient.class))
        .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean("streamingWebClient"));
  }

  @Test
  void should_skip_streaming_client_when_switched_off() {
    runner
        .withPropertyValues("patra.http-client.rest.streaming.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean("streamingWebClient"));
  }
}
