package dev.patra.tool.launch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LaunchAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(LaunchAutoConfiguration.class));

  @Test
  void should_register_utc_clock_by_default() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(Clock.class);
          assertThat(ctx.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.launch.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(Clock.class));
  }

  @Test
  void should_back_off_when_user_defines_clock() {
    Clock custom = Clock.systemDefaultZone();
    runner
        .withBean("myClock", Clock.class, () -> custom)
        .run(ctx -> assertThat(ctx).getBean(Clock.class).isSameAs(custom));
  }
}
