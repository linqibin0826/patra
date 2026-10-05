package dev.linqibin.patra.starter.security.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

/// SecurityExceptionRethrowAdvice 单元测试。
///
/// 这里只验证「原样抛出」和优先级；异常是否真的回到安全过滤器，由任务 10 的
/// 整应用测试验证。
@DisplayName("SecurityExceptionRethrowAdvice 单元测试")
class SecurityExceptionRethrowAdviceTest {

  private final SecurityExceptionRethrowAdvice advice = new SecurityExceptionRethrowAdvice();

  @Test
  @DisplayName("拒绝访问异常被原样抛出")
  void should_rethrow_same_access_denied_exception() {
    AccessDeniedException exception = new AccessDeniedException("denied");

    assertThatThrownBy(() -> advice.rethrowAccessDenied(exception)).isSameAs(exception);
  }

  @Test
  @DisplayName("认证异常被原样抛出")
  void should_rethrow_same_authentication_exception() {
    AuthenticationException exception = new BadCredentialsException("bad credentials");

    assertThatThrownBy(() -> advice.rethrowAuthentication(exception)).isSameAs(exception);
  }

  @Test
  @DisplayName("优先级是最高的，排在全局异常处理器之前")
  void should_have_highest_precedence() {
    Order order = AnnotationUtils.findAnnotation(SecurityExceptionRethrowAdvice.class, Order.class);

    assertThat(order).isNotNull();
    assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }
}
