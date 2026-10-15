package dev.patra.tool.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.observability.filter.CommonTagsMeterFilter;
import dev.patra.tool.observability.filter.HighCardinalityMeterFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObservabilityMicrometerAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ObservabilityAutoConfiguration.class,
                  ObservabilityMicrometerAutoConfiguration.class))
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

  @Test
  void should_register_both_meter_filters_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasSingleBean(HighCardinalityMeterFilter.class)
                .hasSingleBean(CommonTagsMeterFilter.class));
  }

  @Test
  void should_skip_filters_when_metrics_disabled() {
    runner
        .withPropertyValues("patra.observability.metrics.enabled=false")
        .run(
            ctx ->
                assertThat(ctx)
                    .doesNotHaveBean(HighCardinalityMeterFilter.class)
                    .doesNotHaveBean(CommonTagsMeterFilter.class));
  }
}
