package dev.linqibin.patra.common.security;

import java.util.Optional;

/// 取当前用户的端口。
///
/// domain 和 app 层通过它得到「是谁在操作」，不依赖任何 Web 或安全框架。
/// 实现由 `patra-spring-boot-starter-security` 提供。
public interface CurrentUserPort {

  /// 返回当前用户。
  ///
  /// @return 当前用户；匿名请求、不带身份的后台线程返回空
  Optional<CurrentUser> current();

  /// 返回当前用户，没有时抛异常。需要登录的业务入口用它。
  ///
  /// @return 当前用户
  /// @throws AuthenticationRequiredException 没有当前用户时
  default CurrentUser require() {
    return current().orElseThrow(AuthenticationRequiredException::new);
  }
}
