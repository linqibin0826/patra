package dev.patra.identity.app.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.CurrentPasswordMismatchException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.throttle.LoginAttempt;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.tool.core.security.AccountType;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// CurrentPasswordVerifier 单元测试：再认证与改密码共用的校验，每条路径的返回值、异常和结算。
@DisplayName("CurrentPasswordVerifier 单元测试")
class CurrentPasswordVerifierTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final LoginAttempt ATTEMPT = LoginAttempt.of(AccountType.USER, EMAIL, "ticket");
  private static final PlainPassword PASSWORD = PlainPassword.of("Correct-Horse-02");

  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final LoginThrottlePort loginThrottle = mock(LoginThrottlePort.class);
  private final CurrentPasswordVerifier verifier =
      new CurrentPasswordVerifier(
          credentials, passwordHashing, new ThrottledPasswordCheck(loginThrottle));

  /// 默认放行，凭据存在，这次失败不触发上锁。
  @BeforeEach
  void stubHappyPath() {
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenReturn(ATTEMPT);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.empty());
    when(credentials.findByUserId(42L))
        .thenReturn(Optional.of(UserPasswordCredential.restore(7L, 42L, HASH, 0L)));
  }

  @Test
  @DisplayName("密码正确：返回匹配上的哈希，只按成功结算")
  void should_return_matched_hash_and_settle_success() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    assertThat(verifier.verify(EMAIL, 42L, PASSWORD)).isEqualTo(HASH);

    verify(loginThrottle).recordSuccess(ATTEMPT);
    verify(loginThrottle, never()).recordFailure(any());
    verify(loginThrottle, never()).cancel(any());
  }

  @Test
  @DisplayName("密码错误：抛 PASSWORD_MISMATCH，只按失败结算")
  void should_throw_mismatch_and_settle_failure() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> verifier.verify(EMAIL, 42L, PASSWORD))
        .isInstanceOf(CurrentPasswordMismatchException.class);

    verify(loginThrottle).recordFailure(ATTEMPT);
    verify(loginThrottle, never()).recordSuccess(any());
    verify(loginThrottle, never()).cancel(any());
  }

  @Test
  @DisplayName("这次失败触发上锁：抛 429 和锁定时长")
  void should_throw_locked_when_failure_triggers_lock() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.of(Duration.ofMinutes(15)));

    assertThatThrownBy(() -> verifier.verify(EMAIL, 42L, PASSWORD))
        .isInstanceOfSatisfying(
            LoginTemporarilyLockedException.class,
            e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(15)));
  }

  @Test
  @DisplayName("锁定期内：直接 429，不查凭据、不做哈希、不结算")
  void should_reject_when_locked_without_checking() {
    when(loginThrottle.begin(AccountType.USER, EMAIL))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofMinutes(10)));

    assertThatThrownBy(() -> verifier.verify(EMAIL, 42L, PASSWORD))
        .isInstanceOf(LoginTemporarilyLockedException.class);

    verifyNoInteractions(credentials, passwordHashing);
    verify(loginThrottle, never()).recordSuccess(any());
    verify(loginThrottle, never()).recordFailure(any());
    verify(loginThrottle, never()).cancel(any());
  }

  @Test
  @DisplayName("凭据不存在：拿假哈希校验一次，按失败结算")
  void should_verify_against_dummy_when_credential_is_missing() {
    when(credentials.findByUserId(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> verifier.verify(EMAIL, 42L, PASSWORD))
        .isInstanceOf(CurrentPasswordMismatchException.class);

    verify(passwordHashing).verifyAgainstDummy(PASSWORD);
    verify(passwordHashing, never()).matches(any(), any());
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("超过登录长度上限：不查凭据、不做哈希，按失败结算")
  void should_settle_overlong_password_as_failure_without_hashing() {
    PlainPassword overlong = PlainPassword.of("x".repeat(PasswordPolicy.MAX_LOGIN_LENGTH + 1));

    assertThatThrownBy(() -> verifier.verify(EMAIL, 42L, overlong))
        .isInstanceOf(CurrentPasswordMismatchException.class);

    verifyNoInteractions(credentials, passwordHashing);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verify(loginThrottle, never()).recordSuccess(any());
    verify(loginThrottle, never()).cancel(any());
  }

  @Test
  @DisplayName("长度正好在登录上限（按码点算）：照常校验")
  void should_verify_password_at_login_length_limit() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    PlainPassword atLimit = PlainPassword.of("😀".repeat(PasswordPolicy.MAX_LOGIN_LENGTH));

    assertThat(verifier.verify(EMAIL, 42L, atLimit)).isEqualTo(HASH);
  }

  @Test
  @DisplayName("查凭据出错：只按取消结算，异常原样抛出")
  void should_cancel_when_lookup_fails() {
    IllegalStateException boom = new IllegalStateException("db");
    when(credentials.findByUserId(42L)).thenThrow(boom);

    assertThatThrownBy(() -> verifier.verify(EMAIL, 42L, PASSWORD)).isSameAs(boom);

    verify(loginThrottle).cancel(ATTEMPT);
    verify(loginThrottle, never()).recordSuccess(any());
    verify(loginThrottle, never()).recordFailure(any());
  }

  @Test
  @DisplayName("哈希排队超时：只按取消结算，503 原样抛出")
  void should_cancel_when_hashing_is_unavailable() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    when(passwordHashing.matches(any(), any())).thenThrow(busy);

    assertThatThrownBy(() -> verifier.verify(EMAIL, 42L, PASSWORD)).isSameAs(busy);

    verify(loginThrottle).cancel(ATTEMPT);
    verify(loginThrottle, never()).recordSuccess(any());
    verify(loginThrottle, never()).recordFailure(any());
  }

  @Test
  @DisplayName("取消本身也失败：原来的错误不被盖掉")
  void should_keep_original_error_when_cancel_fails() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    when(passwordHashing.matches(any(), any())).thenThrow(busy);
    doThrow(new TemporarilyUnavailableException()).when(loginThrottle).cancel(ATTEMPT);

    assertThatThrownBy(() -> verifier.verify(EMAIL, 42L, PASSWORD)).isSameAs(busy);
  }
}
