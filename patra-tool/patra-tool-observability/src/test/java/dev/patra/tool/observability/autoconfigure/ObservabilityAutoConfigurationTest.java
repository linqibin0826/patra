package dev.patra.tool.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObservabilityAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

  @Test
  void should_register_observation_registry_by_default() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(ObservationRegistry.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.observability.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ObservationRegistry.class));
  }

  @Test
  void should_back_off_when_user_defines_registry() {
    ObservationRegistry custom = ObservationRegistry.create();
    runner
        .withBean("myRegistry", ObservationRegistry.class, () -> custom)
        .run(ctx -> assertThat(ctx).getBean(ObservationRegistry.class).isSameAs(custom));
  }

  @Test
  void should_bind_cluster_default() {
    runner.run(
        ctx ->
            assertThat(ctx.getBean(ObservabilityProperties.class).cluster()).isEqualTo("default"));
  }
}
