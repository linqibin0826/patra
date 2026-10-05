package dev.linqibin.patra.starter.security.error;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/// 把控制器或方法注解里抛出的 Spring Security 异常原样抛回安全过滤器。
///
/// 只有安全过滤器知道当前是匿名还是已登录，才能正确区分 401 和 403。全局异常处理器
/// 会兜住所有异常，所以这个类以最高优先级抢在它前面，只接安全异常并原样抛出。
/// 原样抛出的异常 Spring MVC 当作没处理，会回到安全过滤器，由 `SecurityProblemWriter` 输出。
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionRethrowAdvice {

  /// 原样抛出拒绝访问异常。
  ///
  /// @param exception 控制器里抛出的拒绝访问异常
  @ExceptionHandler(AccessDeniedException.class)
  public void rethrowAccessDenied(AccessDeniedException exception) {
    throw exception;
  }

  /// 原样抛出认证异常。
  ///
  /// @param exception 控制器里抛出的认证异常
  @ExceptionHandler(AuthenticationException.class)
  public void rethrowAuthentication(AuthenticationException exception) {
    throw exception;
  }
}
