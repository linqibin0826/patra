package dev.patra.tool.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.observability.interceptor.ObservationResolutionInterceptor;
import dev.patra.tool.web.error.pipeline.ResolutionInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObservabilityErrorAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ObservabilityAutoConfiguration.class, ObservabilityErrorAutoConfiguration.class));

  @Test
  void should_register_resolution_interceptor_when_web_is_present() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(ObservationResolutionInterceptor.class));
  }

  @Test
  void should_stay_quiet_without_web_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(ResolutionInterceptor.class))
        .run(
            ctx ->
                assertThat(ctx)
                    .hasNotFailed()
                    .doesNotHaveBean(ObservationResolutionInterceptor.class));
  }

  @Test
  void should_skip_interceptor_when_disabled() {
    runner
        .withPropertyValues("patra.observability.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ObservationResolutionInterceptor.class));
  }
}
