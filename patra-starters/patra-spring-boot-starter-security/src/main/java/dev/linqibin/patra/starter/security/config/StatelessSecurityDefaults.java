package dev.linqibin.patra.starter.security.config;

import dev.linqibin.patra.starter.security.error.SecurityProblemWriter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/// Patra 各服务过滤器链共用的无状态默认配置。
///
/// 下游的默认过滤器链用它；网关写自己的过滤器链时调用同一个方法，再加自己的规则。
public final class StatelessSecurityDefaults {

  /// 工具类，不允许实例化。
  private StatelessSecurityDefaults() {}

  /// 应用无状态默认配置。
  ///
  /// - 不创建 HttpSession：安全上下文只放在请求属性里。
  /// - 关 CSRF：凭据在请求头里，不在 Cookie 里。
  /// - 关登出：不关的话 `/logout` 路径会被框架劫持并重定向。
  /// - 未登录输出 401、拒绝访问输出 403，都走统一的写出器。关掉表单登录后，
  ///   框架默认对未登录给的是 403，所以必须显式指定。
  ///
  /// 匿名认证保持框架默认（开）：框架靠匿名认证对象区分「没登录」和「登录了但不允许」。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  public static void apply(HttpSecurity http, SecurityProblemWriter problemWriter) {
    http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(problemWriter)
                    .accessDeniedHandler(problemWriter));
  }
}
