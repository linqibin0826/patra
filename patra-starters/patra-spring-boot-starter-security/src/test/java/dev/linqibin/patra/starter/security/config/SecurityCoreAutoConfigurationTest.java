package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.context.SecurityContextCurrentUserAdapter;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// SecurityCoreAutoConfiguration 自动配置测试。
@DisplayName("SecurityCoreAutoConfiguration 自动配置测试")
class SecurityCoreAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecurityCoreAutoConfiguration.class));

  @Test
  @DisplayName("非 Web 环境下也注册 CurrentUserPort")
  void should_register_current_user_port_without_web_environment() {
    contextRunner.run(
        context ->
            assertThat(context)
                .getBean(CurrentUserPort.class)
                .isInstanceOf(SecurityContextCurrentUserAdapter.class));
  }

  @Test
  @DisplayName("应用自己提供 CurrentUserPort 时让位")
  void should_back_off_when_application_provides_current_user_port() {
    CurrentUserPort custom = Optional::empty;

    contextRunner
        .withBean(CurrentUserPort.class, () -> custom)
        .run(context -> assertThat(context).getBean(CurrentUserPort.class).isSameAs(custom));
  }

  @Test
  @DisplayName("自动配置类登记在 imports 文件里")
  void should_be_registered_in_auto_configuration_imports() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains("dev.linqibin.patra.starter.security.config.SecurityCoreAutoConfiguration");
  }
}
