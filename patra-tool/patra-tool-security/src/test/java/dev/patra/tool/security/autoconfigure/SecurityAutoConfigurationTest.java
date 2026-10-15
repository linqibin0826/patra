package dev.patra.tool.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.core.security.CurrentUserPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SecurityAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

  @Test
  void should_register_current_user_port_without_web_environment() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(CurrentUserPort.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.security.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(CurrentUserPort.class));
  }

  @Test
  void should_back_off_when_application_provides_current_user_port() {
    CurrentUserPort custom = Optional::empty;
    runner
        .withBean("myPort", CurrentUserPort.class, () -> custom)
        .run(ctx -> assertThat(ctx).getBean(CurrentUserPort.class).isSameAs(custom));
  }
}
