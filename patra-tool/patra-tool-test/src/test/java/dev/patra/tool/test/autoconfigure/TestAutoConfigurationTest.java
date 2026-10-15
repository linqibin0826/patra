package dev.patra.tool.test.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TestAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TestAutoConfiguration.class));

  @Test
  void should_register_simple_meter_registry_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasSingleBean(MeterRegistry.class)
                .getBean(MeterRegistry.class)
                .isInstanceOf(SimpleMeterRegistry.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.test.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(MeterRegistry.class));
  }

  @Test
  void should_back_off_when_user_defines_registry() {
    MeterRegistry custom = new SimpleMeterRegistry();
    runner
        .withBean("myRegistry", MeterRegistry.class, () -> custom)
        .run(ctx -> assertThat(ctx).getBean(MeterRegistry.class).isSameAs(custom));
  }

  @Test
  void should_stay_quiet_without_micrometer_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }
}
