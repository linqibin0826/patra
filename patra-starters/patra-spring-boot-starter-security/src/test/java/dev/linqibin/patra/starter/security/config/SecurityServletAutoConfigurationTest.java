package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.error.SecurityErrorMappingContributor;
import dev.linqibin.patra.starter.security.error.SecurityExceptionRethrowAdvice;
import dev.linqibin.patra.starter.security.error.SecurityProblemWriter;
import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/// SecurityServletAutoConfiguration 自动配置测试。
///
/// 把 Spring Boot 自己的安全自动配置一起放进来，验证我们的 Bean 能让它们让位。
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("SecurityServletAutoConfiguration 自动配置测试")
class SecurityServletAutoConfigurationTest {

  private static final String PUBLIC_KEYS_PROPERTY =
      "patra.security.identity-assertion.public-keys=" + publicJwkSet();

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  CoreErrorAutoConfiguration.class,
                  WebErrorAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  UserDetailsServiceAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class,
                  SecurityCoreAutoConfiguration.class,
                  SecurityServletAutoConfiguration.class))
          .withPropertyValues("linqibin.starter.core.error.context-prefix=TEST");

  @Test
  @DisplayName("没配公钥时启动失败，错误信息指明配置项")
  void should_fail_to_start_when_public_keys_missing() {
    contextRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .rootCause()
              .hasMessageContaining("patra.security.identity-assertion.public-keys");
        });
  }

  @Test
  @DisplayName("配了公钥时注册验签器、默认过滤器链和配套 Bean")
  void should_register_default_chain_and_companions_when_public_keys_configured() {
    contextRunner
        .withPropertyValues(PUBLIC_KEYS_PROPERTY)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(JwtDecoder.class);
              assertThat(context).hasSingleBean(SecurityProblemWriter.class);
              assertThat(context).hasSingleBean(SecurityErrorMappingContributor.class);
              assertThat(context).hasSingleBean(SecurityExceptionRethrowAdvice.class);
              assertThat(context.getBeansOfType(SecurityFilterChain.class))
                  .containsOnlyKeys("patraSecurityFilterChain");
            });
  }

  @Test
  @DisplayName("默认过滤器链：认证过滤器排在匿名过滤器之前，没有 CSRF 和登出过滤器")
  void should_build_stateless_chain_with_assertion_filter() {
    contextRunner
        .withPropertyValues(PUBLIC_KEYS_PROPERTY)
        .run(
            context -> {
              SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
              assertThat(chain.getFilters())
                  .extracting(filter -> filter.getClass().getSimpleName())
                  .containsSubsequence("AuthenticationFilter", "AnonymousAuthenticationFilter")
                  .doesNotContain("CsrfFilter", "LogoutFilter");
            });
  }

  @Test
  @DisplayName("应用自己声明过滤器链时默认链让位，无状态默认配置可以被复用")
  void should_back_off_default_chain_when_application_declares_one() {
    contextRunner
        .withPropertyValues(PUBLIC_KEYS_PROPERTY)
        .withUserConfiguration(CustomChainConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(SecurityFilterChain.class))
                  .containsOnlyKeys("customChain");
            });
  }

  @Test
  @DisplayName("非 Web 环境下不注册 servlet 相关的 Bean，也不要求公钥")
  void should_skip_servlet_beans_without_web_environment() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SecurityCoreAutoConfiguration.class, SecurityServletAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(CurrentUserPort.class);
              assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
              assertThat(context).doesNotHaveBean(PatraSecurityProperties.class);
            });
  }

  @Test
  @DisplayName("自动配置类登记在 imports 文件里")
  void should_be_registered_in_auto_configuration_imports() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains("dev.linqibin.patra.starter.security.config.SecurityServletAutoConfiguration");
  }

  @Test
  @DisplayName("不生成带随机密码的默认用户")
  void should_not_generate_default_user(CapturedOutput output) {
    contextRunner
        .withPropertyValues(PUBLIC_KEYS_PROPERTY)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(UserDetailsService.class);
              assertThat(output).doesNotContain("Using generated security password");
            });
  }

  @Test
  @DisplayName("应用自己声明认证管理器时，拒绝一切的那个让位")
  void should_back_off_rejecting_manager_when_application_declares_one() {
    contextRunner
        .withPropertyValues(PUBLIC_KEYS_PROPERTY)
        .withUserConfiguration(CustomAuthenticationManagerConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(AuthenticationManager.class))
                  .containsOnlyKeys("customAuthenticationManager");
            });
  }

  /// 本测试类用的临时公钥，JWK Set JSON。
  private static String publicJwkSet() {
    try {
      ECKey key = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
      return new JWKSet(key.toPublicJWK()).toString(true);
    } catch (JOSEException e) {
      throw new IllegalStateException("生成测试密钥失败", e);
    }
  }

  /// 模拟网关：声明自己的过滤器链，复用无状态默认配置，再加自己的规则。
  @Configuration(proxyBeanMethods = false)
  static class CustomChainConfig {

    /// 自定义过滤器链。
    ///
    /// @param http Spring Security 的构建器
    /// @param problemWriter 统一的错误写出器
    /// @return 过滤器链
    @Bean
    SecurityFilterChain customChain(HttpSecurity http, SecurityProblemWriter problemWriter) {
      StatelessSecurityDefaults.apply(http, problemWriter);
      http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());
      return http.build();
    }
  }

  /// 模拟网关：声明自己的认证管理器。
  @Configuration(proxyBeanMethods = false)
  static class CustomAuthenticationManagerConfig {

    /// 自定义认证管理器。
    ///
    /// @return 认证管理器
    @Bean
    AuthenticationManager customAuthenticationManager() {
      return authentication -> authentication;
    }
  }
}
