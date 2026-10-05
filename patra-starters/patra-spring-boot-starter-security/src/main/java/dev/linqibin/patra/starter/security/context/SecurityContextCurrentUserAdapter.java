package dev.linqibin.patra.starter.security.context;

import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/// `CurrentUserPort` 的实现：从 Spring Security 的安全上下文里取当前用户。
///
/// 线程上下文的设置和清理由 Spring Security 的过滤器负责，这里只读。
public class SecurityContextCurrentUserAdapter implements CurrentUserPort {

  /// 返回当前用户。
  ///
  /// 判断的是主体的类型，不是 `isAuthenticated()`：匿名认证对象的 `isAuthenticated()`
  /// 也是 true，但它的主体是一个字符串，按类型判断不会把它误认成用户。
  ///
  /// @return 当前用户；安全上下文为空、匿名、主体是别的类型时返回空
  @Override
  public Optional<CurrentUser> current() {
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
      return Optional.of(user);
    }
    return Optional.empty();
  }
}
