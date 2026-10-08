package dev.linqibin.patra.starter.security.config;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionDecoders;
import dev.linqibin.patra.starter.security.authentication.IdentityAssertionAuthenticationConverter;
import dev.linqibin.patra.starter.security.error.SecurityErrorMappingContributor;
import dev.linqibin.patra.starter.security.error.SecurityExceptionRethrowAdvice;
import dev.linqibin.patra.starter.security.error.SecurityProblemWriter;
import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/// 安全 starter 的 servlet 自动配置：验签器、过滤器链、错误输出。
///
/// 排在 Spring Boot 的默认用户和默认过滤器链之前，它们检测到这里的 Bean 后会让位。
@AutoConfiguration(
    before = {UserDetailsServiceAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PatraSecurityProperties.class)
public class SecurityServletAutoConfiguration {

  /// 注册统一的错误写出器。
  ///
  /// @param problemDetailAdapter 把异常变成 ProblemDetail 的适配器
  /// @param jsonMapper 容器里的 JSON 映射器
  /// @return 错误写出器
  @Bean
  @ConditionalOnMissingBean
  public SecurityProblemWriter securityProblemWriter(
      ProblemDetailAdapter problemDetailAdapter, JsonMapper jsonMapper) {
    return new SecurityProblemWriter(problemDetailAdapter, jsonMapper);
  }

  /// 注册安全异常到错误码的映射。
  ///
  /// @param http 带服务前缀的标准 HTTP 错误码组
  /// @return 错误码映射
  @Bean
  @ConditionalOnMissingBean
  public SecurityErrorMappingContributor securityErrorMappingContributor(HttpStdErrors.Group http) {
    return new SecurityErrorMappingContributor(http);
  }

  /// 注册把控制器里的安全异常抛回过滤器的处理器。
  ///
  /// 服务扫描整个 `dev.linqibin` 时这个类会先被组件扫描注册，这里随之让位。
  ///
  /// @return 重抛处理器
  @Bean
  @ConditionalOnMissingBean
  public SecurityExceptionRethrowAdvice securityExceptionRethrowAdvice() {
    return new SecurityExceptionRethrowAdvice();
  }

  /// 注册一个拒绝一切的认证管理器，让 Spring Boot 的默认用户让位。
  ///
  /// Boot 检测不到任何认证相关的 Bean 时，会生成一个带随机密码的内存用户并把密码
  /// 打进日志。应用里没有用户名密码登录，这个 Bean 不会被任何地方调用。
  /// 应用自己声明了这四类 Bean 中的任何一个时（比如网关的会话认证管理器），它让位。
  ///
  /// @return 拒绝一切的认证管理器
  @Bean
  @ConditionalOnMissingBean({
    AuthenticationManager.class,
    AuthenticationProvider.class,
    UserDetailsService.class,
    AuthenticationManagerResolver.class
  })
  public AuthenticationManager rejectingAuthenticationManager() {
    return authentication -> {
      throw new ProviderNotFoundException("本应用没有用户名密码登录");
    };
  }

  /// 用配置里的公钥建验签器。容器里有 `Clock` 就用它，没有用系统 UTC 时钟。
  ///
  /// @param properties 配置属性
  /// @param clock 容器里的时钟，可能没有
  /// @return 验签器
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder identityAssertionDecoder(
      PatraSecurityProperties properties, ObjectProvider<Clock> clock) {
    return IdentityAssertionDecoders.forPublicKeys(
        properties.identityAssertion().publicKeySet(), clock.getIfAvailable(Clock::systemUTC));
  }

  /// 下游的默认过滤器链：无会话，从 `Authorization: Bearer` 里的断言建立认证，所有路径放行。
  ///
  /// 下游不做路径级拦截，路由规则归网关。需要登录的接口由业务代码调
  /// `CurrentUserPort.require()` 来保证，这样服务之间直连的 `/_internal/**` 不受影响。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  /// @param identityAssertionDecoder 验签器
  /// @return 过滤器链
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  public SecurityFilterChain patraSecurityFilterChain(
      HttpSecurity http, SecurityProblemWriter problemWriter, JwtDecoder identityAssertionDecoder) {
    StatelessSecurityDefaults.apply(http, problemWriter);

    // 转换器给出的已经是认证完成的对象，原样返回；不经过 ProviderManager，凭据不会被擦掉。
    // 必须用显式类型：AuthenticationFilter 的两个构造器对 lambda 有二义性。
    AuthenticationManager passThrough = authentication -> authentication;
    // 不声明成 Bean：否则 Boot 会把它再注册成全局 servlet 过滤器。
    AuthenticationFilter assertionFilter =
        new AuthenticationFilter(
            passThrough, new IdentityAssertionAuthenticationConverter(identityAssertionDecoder));
    // 默认的成功处理器会回 302 再继续执行过滤器链，换成什么都不做。
    assertionFilter.setSuccessHandler((request, response, authentication) -> {});
    // 默认的失败处理器遇到服务类异常会原样抛出，换成统一的写出器。
    assertionFilter.setFailureHandler(problemWriter);

    http.addFilterBefore(assertionFilter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .anyRequest()
                    .permitAll());
    return http.build();
  }
}
