package dev.patra.tool.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.cqrs.interceptor.LoggingCommandInterceptor;
import dev.patra.tool.cqrs.interceptor.MetricsCommandInterceptor;
import dev.patra.tool.cqrs.interceptor.TracingCommandInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CqrsAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class));

  @Test
  void should_register_command_bus_and_logging_interceptor_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasSingleBean(CommandBus.class)
                .hasSingleBean(LoggingCommandInterceptor.class)
                .doesNotHaveBean(TracingCommandInterceptor.class)
                .doesNotHaveBean(MetricsCommandInterceptor.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.cqrs.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(CommandBus.class));
  }

  @Test
  void should_back_off_when_user_defines_command_bus() {
    CommandBus custom = mock(CommandBus.class);
    runner
        .withBean("customBus", CommandBus.class, () -> custom)
        .run(ctx -> assertThat(ctx).getBean(CommandBus.class).isSameAs(custom));
  }

  @Test
  void should_add_tracing_and_metrics_interceptors_when_registries_exist() {
    runner
        .withBean(ObservationRegistry.class, ObservationRegistry::create)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            ctx ->
                assertThat(ctx)
                    .hasSingleBean(TracingCommandInterceptor.class)
                    .hasSingleBean(MetricsCommandInterceptor.class));
  }

  @Test
  void should_add_metrics_interceptor_when_boot_builds_the_meter_registry() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CqrsAutoConfiguration.class,
                MetricsAutoConfiguration.class,
                CompositeMeterRegistryAutoConfiguration.class,
                SimpleMetricsExportAutoConfiguration.class))
        .run(ctx -> assertThat(ctx).hasSingleBean(MetricsCommandInterceptor.class));
  }

  @Test
  void should_skip_optional_interceptors_without_micrometer_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(ObservationRegistry.class, MeterRegistry.class))
        .run(
            ctx ->
                assertThat(ctx)
                    .hasSingleBean(CommandBus.class)
                    .hasSingleBean(LoggingCommandInterceptor.class));
  }

  @Test
  void should_honour_interceptor_switches() {
    runner
        .withPropertyValues("patra.cqrs.interceptors.logging=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(LoggingCommandInterceptor.class));
  }
}
