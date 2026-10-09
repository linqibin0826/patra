package dev.linqibin.patra.identity.app.usecase.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.InvalidCredentialsException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.exception.UserBannedException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import dev.linqibin.patra.identity.domain.service.SessionIssuer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/// LoginUserHandler 单元测试。
@DisplayName("LoginUserHandler 单元测试")
class LoginUserHandlerTest {

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
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final LoginUserHandler handler =
      new LoginUserHandler(
          users,
          credentials,
          passwordHashing,
          loginThrottle,
          sessionIssuer,
          TransactionOperations.withoutTransaction());

  /// 默认放行，用户和凭据都存在。
  @BeforeEach
  void stubHappyPath() {
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenReturn(ATTEMPT);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ACTIVE_USER));
    when(credentials.findByUserId(42L)).thenReturn(Optional.of(CREDENTIAL));
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.empty());
    when(sessionIssuer.issue(anyLong(), any()))
        .thenReturn(IssuedUserSession.of("patra_user_x", List.of()));
  }

  @Test
  @DisplayName("密码正确：按成功结算，返回用户 ID 和邮箱")
  void should_authenticate_with_correct_password() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    LoginUserResult result =
        handler.handle(LoginUserCommand.of("Chen.Yu@Example.com", "correct horse", null, null));

    assertThat(result.userId()).isEqualTo(42L);
    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    verify(loginThrottle).recordSuccess(ATTEMPT);
    verify(loginThrottle, never()).recordFailure(any());
    verify(loginThrottle, never()).cancel(any());
  }

  @Test
  @DisplayName("密码错误：按失败结算，返回 401")
  void should_reject_wrong_password() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "wrong", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("这次失败触发上锁：返回 429 和锁定时长")
  void should_return_429_when_failure_triggers_lock() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.of(Duration.ofMinutes(15)));

    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "wrong", null, null)))
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
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "any", null, null)))
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
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "any", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).verifyAgainstDummy(any());
  }

  @Test
  @DisplayName("锁定期间直接返回 429，不查库、不做哈希")
  void should_reject_when_locked_without_touching_storage() {
    when(loginThrottle.begin(AccountType.USER, EMAIL))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofMinutes(10)));

    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "any", null, null)))
        .isInstanceOf(LoginTemporarilyLockedException.class);
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("封禁的账号：密码对返回 403")
  void should_return_403_for_banned_user_with_correct_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "right", null, null)))
        .isInstanceOf(UserBannedException.class);
    verify(loginThrottle).recordSuccess(ATTEMPT);
  }

  @Test
  @DisplayName("封禁的账号：密码错仍是 401，不暴露封禁")
  void should_return_401_for_banned_user_with_wrong_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "wrong", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("哈希排队超时：按取消结算，不计失败，503 原样传出")
  void should_cancel_attempt_when_hashing_is_unavailable() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    when(passwordHashing.matches(any(), any())).thenThrow(busy);

    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "any", null, null)))
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
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "any", null, null)))
        .isSameAs(busy);
  }

  @Test
  @DisplayName("字段为 null 时报 REQUIRED，不进失败限制")
  void should_validate_fields_before_throttling() {
    assertThatThrownBy(() -> handler.handle(LoginUserCommand.of(null, null, null, null)))
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
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "abc", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).matches(any(), any());
  }

  @Test
  @DisplayName("超过登录长度上限的密码：不查库、不做哈希，按失败结算，返回 401")
  void should_reject_overlong_password_without_hashing() {
    String overlong = "x".repeat(PasswordPolicy.MAX_LOGIN_LENGTH + 1);

    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", overlong, null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("长度正好在登录上限（按码点算）的密码照常校验")
  void should_verify_password_at_login_length_limit() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    handler.handle(
        LoginUserCommand.of(
            "chen.yu@example.com", "😀".repeat(PasswordPolicy.MAX_LOGIN_LENGTH), null, null));

    verify(passwordHashing).matches(any(), any());
  }

  @Test
  @DisplayName("1 MB 的邮箱：按字段校验报 TOO_LONG，不进失败限制")
  void should_reject_huge_email_by_field_validation() {
    String hugeEmail = "a".repeat(1_000_000) + "@example.com";

    assertThatThrownBy(() -> handler.handle(LoginUserCommand.of(hugeEmail, "any", null, null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:TOO_LONG"));
    verifyNoInteractions(loginThrottle);
  }

  @Test
  @DisplayName("命令的 toString 不输出密码")
  void should_hide_password_in_command_to_string() {
    assertThat(LoginUserCommand.of("chen.yu@example.com", "Secret-Value-1", null, null).toString())
        .doesNotContain("Secret-Value-1");
  }

  @Test
  @DisplayName("凭据正确：在结算成功之后、以已校验的用户 ID 和客户端信息建会话")
  void should_issue_session_after_successful_authentication() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    LoginUserResult result =
        handler.handle(LoginUserCommand.of("chen.yu@example.com", "correct horse", "web", " mac "));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    ArgumentCaptor<LoginClient> client = ArgumentCaptor.forClass(LoginClient.class);
    verify(sessionIssuer).issue(eq(42L), client.capture());
    assertThat(client.getValue().device()).map(DeviceId::value).contains("mac");
    var order = inOrder(loginThrottle, sessionIssuer);
    order.verify(loginThrottle).recordSuccess(ATTEMPT);
    order.verify(sessionIssuer).issue(anyLong(), any());
  }

  @Test
  @DisplayName("密码错误、账号被封禁：都不建会话")
  void should_not_issue_session_when_rejected() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "wrong", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);

    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    assertThatThrownBy(
            () -> handler.handle(LoginUserCommand.of("chen.yu@example.com", "correct", null, null)))
        .isInstanceOf(UserBannedException.class);

    verifyNoInteractions(sessionIssuer);
  }

  @Test
  @DisplayName("客户端类型和设备标识的错误和邮箱、密码一起报，不碰限流")
  void should_report_client_violations_with_other_fields() {
    assertThatThrownBy(() -> handler.handle(LoginUserCommand.of("", "", "app", "x".repeat(129))))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field)
                    .containsExactly("email", "password", "clientType", "deviceId"));
    verifyNoInteractions(loginThrottle, sessionIssuer);
  }

  @Test
  @DisplayName("命令的 toString 不带密码")
  void should_mask_password_in_command() {
    assertThat(LoginUserCommand.of("a@example.com", "secret", "web", null).toString())
        .doesNotContain("secret")
        .contains("a@example.com");
    assertThat(LoginUserResult.of("patra_user_x", 42L, "a@example.com").toString())
        .doesNotContain("patra_user_x")
        .contains("42");
  }
}
