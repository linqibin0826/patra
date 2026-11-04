package dev.patra.tool.security.autoconfigure;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.security.web.SecurityFilterChain;

/// 应用在默认过滤器链之外多加的一条链：声明它时默认链不让位，两条链共存。
///
/// 声明普通的 `SecurityFilterChain` 表示整套替代默认链（网关）；只想给几个路径多加一条（identity 的
/// `oauth2Login`）时，工厂方法的返回类型写这个类，再用 `@Order` 排到默认链前面，用
/// `securityMatcher` 限定路径。默认链的条件按工厂方法的返回类型判断，写成 `SecurityFilterChain` 认不出来。
public final class AdditionalSecurityFilterChain implements SecurityFilterChain {

  private final SecurityFilterChain delegate;

  /// 包一条已经构建好的链。
  ///
  /// @param delegate 实际的过滤器链
  public AdditionalSecurityFilterChain(SecurityFilterChain delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate 不能为 null");
  }

  /// 这条链是否处理这个请求。
  ///
  /// @param request 当前请求
  /// @return 处理时为 `true`
  @Override
  public boolean matches(HttpServletRequest request) {
    return delegate.matches(request);
  }

  /// 这条链上的过滤器。
  ///
  /// @return 过滤器
  @Override
  public List<Filter> getFilters() {
    return delegate.getFilters();
  }
}
