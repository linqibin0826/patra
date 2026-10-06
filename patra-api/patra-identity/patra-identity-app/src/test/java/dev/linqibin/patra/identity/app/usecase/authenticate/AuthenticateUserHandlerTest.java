package dev.linqibin.patra.identity.app.usecase.authenticate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.InvalidCredentialsException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.exception.UserBannedException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// AuthenticateUserHandler 单元测试。
@DisplayName("AuthenticateUserHandler 单元测试")
class AuthenticateUserHandlerTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final User ACTIVE_USER =
      User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null);
  private static final User BANNED_USER =
      User.restore(
          42L, EMAIL, UserStatus.BANNED, Instant.parse("2026-10-06T08:00:00Z"), 1L, null, null);
  private static final UserPasswordCredential CREDENTIAL =
      UserPasswordCredential.restore(7L, 42L, HASH, 0L);
  private static final LoginAttempt ATTEMPT = LoginAttempt.of(AccountType.USER, EMAIL, "ticket");

  private final UserRepository users = mock(UserRepository.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final LoginThrottlePort loginThrottle = mock(LoginThrottlePort.class);
  private final AuthenticateUserHandler handler =
      new AuthenticateUserHandler(users, credentials, passwordHashing, loginThrottle);

  /// 默认放行，用户和凭据都存在。
  @BeforeEach
  void stubHappyPath() {
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenReturn(ATTEMPT);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ACTIVE_USER));
    when(credentials.findByUserId(42L)).thenReturn(Optional.of(CREDENTIAL));
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("密码正确：按成功结算，返回用户 ID 和邮箱")
  void should_authenticate_with_correct_password() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    AuthenticateUserResult result =
        handler.handle(AuthenticateUserCommand.of("Chen.Yu@Example.com", "correct horse"));

    assertThat(result.userId()).isEqualTo(42L);
    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    verify(loginThrottle).recordSuccess(ATTEMPT);
    verify(loginThrottle, never()).recordFailure(any());
    verify(loginThrottle, never()).cancel(any());
  }

  @Test
  @DisplayName("密码错误：按失败结算，返回 401")
  void should_reject_wrong_password() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("这次失败触发上锁：返回 429 和锁定时长")
  void should_return_429_when_failure_triggers_lock() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.of(Duration.ofMinutes(15)));

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "wrong")))
        .isInstanceOf(LoginTemporarilyLockedException.class)
        .satisfies(
            e ->
                assertThat(((LoginTemporarilyLockedException) e).getRetryAfter())
                    .isEqualTo(Duration.ofMinutes(15)));
  }

  @Test
  @DisplayName("邮箱不存在：照样做一次假哈希校验，按失败结算，返回和密码错一样的 401")
  void should_treat_unknown_email_like_wrong_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).verifyAgainstDummy(any());
    verify(passwordHashing, never()).matches(any(), any());
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("用户在但凭据不在：也按密码错处理")
  void should_treat_missing_credential_like_wrong_password() {
    when(credentials.findByUserId(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).verifyAgainstDummy(any());
  }

  @Test
  @DisplayName("锁定期间直接返回 429，不查库、不做哈希")
  void should_reject_when_locked_without_touching_storage() {
    when(loginThrottle.begin(AccountType.USER, EMAIL))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofMinutes(10)));

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isInstanceOf(LoginTemporarilyLockedException.class);
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("封禁的账号：密码对返回 403")
  void should_return_403_for_banned_user_with_correct_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "right")))
        .isInstanceOf(UserBannedException.class);
    verify(loginThrottle).recordSuccess(ATTEMPT);
  }

  @Test
  @DisplayName("封禁的账号：密码错仍是 401，不暴露封禁")
  void should_return_401_for_banned_user_with_wrong_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("哈希排队超时：按取消结算，不计失败，503 原样传出")
  void should_cancel_attempt_when_hashing_is_unavailable() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    when(passwordHashing.matches(any(), any())).thenThrow(busy);

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isSameAs(busy);
    verify(loginThrottle).cancel(ATTEMPT);
    verify(loginThrottle, never()).recordFailure(any());
  }

  @Test
  @DisplayName("取消本身也失败时，原来的错误不被盖掉")
  void should_keep_original_error_when_cancel_fails() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    when(passwordHashing.matches(any(), any())).thenThrow(busy);
    doThrow(new TemporarilyUnavailableException()).when(loginThrottle).cancel(ATTEMPT);

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "any")))
        .isSameAs(busy);
  }

  @Test
  @DisplayName("字段为 null 时报 REQUIRED，不进失败限制")
  void should_validate_fields_before_throttling() {
    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of(null, null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:REQUIRED", "password:REQUIRED"));
    verifyNoInteractions(loginThrottle);
  }

  @Test
  @DisplayName("登录不按注册规则查长度：比注册下限还短的密码照常校验，错了按凭据错误处理")
  void should_not_apply_registration_length_rules_on_login() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", "abc")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).matches(any(), any());
  }

  @Test
  @DisplayName("超过登录长度上限的密码：不查库、不做哈希，按失败结算，返回 401")
  void should_reject_overlong_password_without_hashing() {
    String overlong = "x".repeat(PasswordPolicy.MAX_LOGIN_LENGTH + 1);

    assertThatThrownBy(
            () -> handler.handle(AuthenticateUserCommand.of("chen.yu@example.com", overlong)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("长度正好在登录上限（按码点算）的密码照常校验")
  void should_verify_password_at_login_length_limit() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    handler.handle(
        AuthenticateUserCommand.of(
            "chen.yu@example.com", "😀".repeat(PasswordPolicy.MAX_LOGIN_LENGTH)));

    verify(passwordHashing).matches(any(), any());
  }

  @Test
  @DisplayName("1 MB 的邮箱：按字段校验报 TOO_LONG，不进失败限制")
  void should_reject_huge_email_by_field_validation() {
    String hugeEmail = "a".repeat(1_000_000) + "@example.com";

    assertThatThrownBy(() -> handler.handle(AuthenticateUserCommand.of(hugeEmail, "any")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:TOO_LONG"));
    verifyNoInteractions(loginThrottle);
  }
}
