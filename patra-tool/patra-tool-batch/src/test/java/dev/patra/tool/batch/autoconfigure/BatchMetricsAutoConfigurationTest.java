package dev.patra.tool.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.batch.metrics.BatchProgressMetricsListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BatchMetricsAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BatchMetricsAutoConfiguration.class));

  @Test
  void should_register_listener_when_meter_registry_exists() {
    runner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(ctx -> assertThat(ctx).hasSingleBean(BatchProgressMetricsListener.class));
  }

  @Test
  void should_register_listener_when_boot_builds_the_meter_registry() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                BatchMetricsAutoConfiguration.class,
                MetricsAutoConfiguration.class,
                CompositeMeterRegistryAutoConfiguration.class,
                SimpleMetricsExportAutoConfiguration.class))
        .run(ctx -> assertThat(ctx).hasSingleBean(BatchProgressMetricsListener.class));
  }

  @Test
  void should_skip_listener_without_meter_registry() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(BatchProgressMetricsListener.class));
  }

  @Test
  void should_skip_listener_when_switched_off() {
    runner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withPropertyValues("patra.batch.metrics.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(BatchProgressMetricsListener.class));
  }

  @Test
  void should_stay_quiet_without_micrometer_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }
}
