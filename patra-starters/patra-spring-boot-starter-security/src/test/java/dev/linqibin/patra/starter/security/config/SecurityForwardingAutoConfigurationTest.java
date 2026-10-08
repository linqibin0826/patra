package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.starter.security.forward.IdentityAssertionForwardingInterceptor;
import dev.linqibin.starter.httpinterface.interceptor.InternalCallInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/// SecurityForwardingAutoConfiguration 自动配置测试。
@DisplayName("SecurityForwardingAutoConfiguration 自动配置测试")
class SecurityForwardingAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SecurityCoreAutoConfiguration.class, SecurityForwardingAutoConfiguration.class));

  @Test
  @DisplayName("classpath 上有 http-interface starter：注册转发拦截器，它是一个 InternalCallInterceptor")
  void should_register_interceptor_when_http_interface_starter_present() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(IdentityAssertionForwardingInterceptor.class);
          assertThat(context).hasSingleBean(InternalCallInterceptor.class);
        });
  }

  @Test
  @DisplayName("classpath 上没有 http-interface starter：不注册，应用照常启动")
  void should_skip_when_http_interface_starter_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(InternalCallInterceptor.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.containsBean("identityAssertionForwardingInterceptor")).isFalse();
            });
  }

  @Test
  @DisplayName("自动配置类登记在 imports 文件里")
  void should_be_registered_in_auto_configuration_imports() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains("dev.linqibin.patra.starter.security.config.SecurityForwardingAutoConfiguration");
  }
}
