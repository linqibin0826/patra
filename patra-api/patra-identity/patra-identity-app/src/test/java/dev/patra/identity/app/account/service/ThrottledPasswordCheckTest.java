package dev.patra.identity.app.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.throttle.LoginAttempt;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.tool.core.security.AccountType;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// ThrottledPasswordCheck 单元测试：每个分支恰好结算一次，且只结算应有的那一种。
@DisplayName("ThrottledPasswordCheck 单元测试")
class ThrottledPasswordCheckTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final LoginAttempt ATTEMPT = LoginAttempt.of(AccountType.USER, EMAIL, "ticket");
  private static final PlainPassword PASSWORD = PlainPassword.of("Correct-Horse-02");

  /// 校验不通过时由调用方决定的异常。
  private static final class MismatchException extends RuntimeException {}

  private final LoginThrottlePort loginThrottle = mock(LoginThrottlePort.class);
  private final ThrottledPasswordCheck check = new ThrottledPasswordCheck(loginThrottle);

  /// 默认放行，失败不触发上锁。
  @BeforeEach
  void stubThrottle() {
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenReturn(ATTEMPT);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("校验通过：先开始、再校验、后结算，只按成功结算一次，返回校验结果")
  void should_settle_success_exactly_once_when_check_matches() {
    Supplier<Optional<String>> matched =
        () -> {
          verify(loginThrottle).begin(AccountType.USER, EMAIL);
          verifyNoMoreInteractions(loginThrottle);
          return Optional.of("matched");
        };

    String result = check.run(EMAIL, PASSWORD, matched, MismatchException::new);

    assertThat(result).isEqualTo("matched");
    InOrder order = inOrder(loginThrottle);
    order.verify(loginThrottle).begin(AccountType.USER, EMAIL);
    order.verify(loginThrottle).recordSuccess(ATTEMPT);
    verifyNoMoreInteractions(loginThrottle);
  }

  @Test
  @DisplayName("校验不通过：只按失败结算一次，抛调用方给的异常")
  void should_settle_failure_exactly_once_when_check_does_not_match() {
    assertThatThrownBy(() -> check.run(EMAIL, PASSWORD, Optional::empty, MismatchException::new))
        .isInstanceOf(MismatchException.class);

    verify(loginThrottle).begin(AccountType.USER, EMAIL);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verifyNoMoreInteractions(loginThrottle);
  }

  @Test
  @DisplayName("这次失败触发上锁：只按失败结算一次，抛 429 和锁定时长")
  void should_throw_locked_when_failure_triggers_lock() {
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.of(Duration.ofMinutes(15)));

    assertThatThrownBy(() -> check.run(EMAIL, PASSWORD, Optional::empty, MismatchException::new))
        .isInstanceOfSatisfying(
            LoginTemporarilyLockedException.class,
            e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(15)));

    verify(loginThrottle).begin(AccountType.USER, EMAIL);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verifyNoMoreInteractions(loginThrottle);
  }

  @Test
  @DisplayName("超过登录长度上限：不调校验，只按失败结算一次")
  void should_settle_overlong_password_as_failure_without_checking() {
    PlainPassword overlong = PlainPassword.of("x".repeat(PasswordPolicy.MAX_LOGIN_LENGTH + 1));
    AtomicInteger calls = new AtomicInteger();
    Supplier<Optional<String>> counting =
        () -> {
          calls.incrementAndGet();
          return Optional.of("matched");
        };

    assertThatThrownBy(() -> check.run(EMAIL, overlong, counting, MismatchException::new))
        .isInstanceOf(MismatchException.class);

    assertThat(calls).hasValue(0);
    verify(loginThrottle).begin(AccountType.USER, EMAIL);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verifyNoMoreInteractions(loginThrottle);
  }

  @Test
  @DisplayName("长度正好在登录上限（按码点算）：照常校验")
  void should_check_password_at_login_length_limit() {
    PlainPassword atLimit = PlainPassword.of("😀".repeat(PasswordPolicy.MAX_LOGIN_LENGTH));
    Supplier<Optional<String>> matched = () -> Optional.of("matched");

    assertThat(check.run(EMAIL, atLimit, matched, MismatchException::new)).isEqualTo("matched");
  }

  @Test
  @DisplayName("校验中途出错：只按取消结算一次，异常原样抛出")
  void should_cancel_exactly_once_when_check_throws() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    Supplier<Optional<String>> failing =
        () -> {
          throw busy;
        };

    assertThatThrownBy(() -> check.run(EMAIL, PASSWORD, failing, MismatchException::new))
        .isSameAs(busy);

    verify(loginThrottle).begin(AccountType.USER, EMAIL);
    verify(loginThrottle).cancel(ATTEMPT);
    verifyNoMoreInteractions(loginThrottle);
  }

  @Test
  @DisplayName("取消本身也失败：原来的错误不被盖掉")
  void should_keep_original_error_when_cancel_fails() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    Supplier<Optional<String>> failing =
        () -> {
          throw busy;
        };
    doThrow(new IllegalStateException("redis")).when(loginThrottle).cancel(ATTEMPT);

    assertThatThrownBy(() -> check.run(EMAIL, PASSWORD, failing, MismatchException::new))
        .isSameAs(busy);
  }

  @Test
  @DisplayName("开始就被拒（锁定期或在途已满）：不调校验，不结算")
  void should_neither_check_nor_settle_when_begin_is_rejected() {
    LoginTemporarilyLockedException locked =
        new LoginTemporarilyLockedException(Duration.ofMinutes(10));
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenThrow(locked);
    AtomicInteger calls = new AtomicInteger();
    Supplier<Optional<String>> counting =
        () -> {
          calls.incrementAndGet();
          return Optional.of("matched");
        };

    assertThatThrownBy(() -> check.run(EMAIL, PASSWORD, counting, MismatchException::new))
        .isSameAs(locked);

    assertThat(calls).hasValue(0);
    verify(loginThrottle).begin(AccountType.USER, EMAIL);
    verifyNoMoreInteractions(loginThrottle);
  }

  @Test
  @DisplayName("结算成功时 Redis 出错：异常原样抛出，不再补一次取消")
  void should_not_settle_again_when_record_success_fails() {
    TemporarilyUnavailableException down = new TemporarilyUnavailableException();
    doThrow(down).when(loginThrottle).recordSuccess(any());
    Supplier<Optional<String>> matched = () -> Optional.of("matched");

    assertThatThrownBy(() -> check.run(EMAIL, PASSWORD, matched, MismatchException::new))
        .isSameAs(down);

    verify(loginThrottle).begin(AccountType.USER, EMAIL);
    verify(loginThrottle).recordSuccess(ATTEMPT);
    verifyNoMoreInteractions(loginThrottle);
  }

  @Test
  @DisplayName("结算失败时 Redis 出错：异常原样抛出，不再补一次取消")
  void should_not_settle_again_when_record_failure_fails() {
    TemporarilyUnavailableException down = new TemporarilyUnavailableException();
    when(loginThrottle.recordFailure(ATTEMPT)).thenThrow(down);

    assertThatThrownBy(() -> check.run(EMAIL, PASSWORD, Optional::empty, MismatchException::new))
        .isSameAs(down);

    verify(loginThrottle).begin(AccountType.USER, EMAIL);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verifyNoMoreInteractions(loginThrottle);
  }
}
