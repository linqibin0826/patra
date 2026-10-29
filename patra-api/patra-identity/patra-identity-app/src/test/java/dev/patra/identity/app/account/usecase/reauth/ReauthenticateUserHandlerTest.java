package dev.patra.identity.app.account.usecase.reauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.service.CurrentPasswordVerifier;
import dev.patra.identity.domain.account.exception.CurrentPasswordMismatchException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.LoginAttempt;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.SessionRotation;
import dev.patra.identity.domain.session.policy.ReauthenticationPolicy;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// ReauthenticateUserHandler 单元测试：字段、状态、共用登录失败限制、换令牌。
@DisplayName("ReauthenticateUserHandler 单元测试")
class ReauthenticateUserHandlerTest {

  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final LoginAttempt ATTEMPT = LoginAttempt.of(AccountType.USER, EMAIL, "ticket");
  private static final String NEW_TOKEN = "patra_user_" + "N".repeat(43);
  private static final UserAccountReadModel ACCOUNT =
      UserAccountReadModel.builder()
          .userId(42L)
          .email("chen.yu@example.com")
          .emailVerified(true)
          .username("chen-yu")
          .displayName("陈宇")
          .status(UserStatus.ACTIVE)
          .build();

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final LoginThrottlePort loginThrottle = mock(LoginThrottlePort.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final ReauthenticateUserHandler handler =
      new ReauthenticateUserHandler(
          currentUserPort,
          users,
          userReadPort,
          new CurrentPasswordVerifier(credentials, passwordHashing, loginThrottle),
          sessions,
          ReauthenticationPolicy.of(Duration.ofMinutes(10), Duration.ofSeconds(10)));

  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findById(42L)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
    when(credentials.findByUserId(42L))
        .thenReturn(Optional.of(UserPasswordCredential.restore(7L, 42L, HASH, 0L)));
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenReturn(ATTEMPT);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.empty());
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(sessions.rotate(AccountType.USER, 42L, 7001L, Duration.ofSeconds(10)))
        .thenReturn(Optional.of(SessionRotation.rotated(NEW_TOKEN)));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
  }

  @Test
  @DisplayName("密码为空：422，不碰登录失败限制")
  void should_reject_blank_password_with_422() {
    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("")))
        .isInstanceOf(InvalidUserFieldsException.class);
    verifyNoInteractions(loginThrottle, sessions);
  }

  @Test
  @DisplayName("封禁或已注销：401，不碰登录失败限制")
  void should_require_login_when_user_is_banned_or_deleted() {
    when(users.findById(42L)).thenReturn(Optional.of(user(UserStatus.BANNED)));

    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("Correct-Horse-02")))
        .isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(loginThrottle, sessions);
  }

  @Test
  @DisplayName("注销冷静期内可以再认证")
  void should_allow_pending_deletion_user() {
    when(users.findById(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));

    Reauthenticated result = handler.handle(ReauthenticateUserCommand.of("Correct-Horse-02"));

    assertThat(result.sessionToken()).isEqualTo(NEW_TOKEN);
  }

  @Test
  @DisplayName("密码正确：开始 → 校验 → 成功结算 → 换令牌，返回新令牌和账号信息")
  void should_rotate_session_and_return_new_token_after_success() {
    Reauthenticated result = handler.handle(ReauthenticateUserCommand.of("Correct-Horse-02"));

    InOrder order = inOrder(loginThrottle, passwordHashing, userReadPort, sessions);
    order.verify(loginThrottle).begin(AccountType.USER, EMAIL);
    order.verify(passwordHashing).matches(any(), any());
    order.verify(loginThrottle).recordSuccess(ATTEMPT);
    order.verify(userReadPort).findAccount(42L);
    order.verify(sessions).rotate(AccountType.USER, 42L, 7001L, Duration.ofSeconds(10));
    assertThat(result.sessionToken()).isEqualTo(NEW_TOKEN);
    assertThat(result.account()).isEqualTo(ACCOUNT);
  }

  @Test
  @DisplayName("密码错误：按失败结算，401 PASSWORD_MISMATCH，不换令牌")
  void should_record_failure_and_throw_password_mismatch() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("wrong")))
        .isInstanceOf(CurrentPasswordMismatchException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verifyNoInteractions(sessions);
  }

  @Test
  @DisplayName("这次失败触发上锁：429 和锁定时长")
  void should_return_429_when_failure_locks() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.of(Duration.ofMinutes(15)));

    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("wrong")))
        .isInstanceOf(LoginTemporarilyLockedException.class);
  }

  @Test
  @DisplayName("锁定期间直接 429，不查凭据、不做哈希")
  void should_reject_when_locked_before_hashing() {
    when(loginThrottle.begin(AccountType.USER, EMAIL))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofMinutes(10)));

    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("any")))
        .isInstanceOf(LoginTemporarilyLockedException.class);
    verifyNoInteractions(credentials, passwordHashing, sessions);
  }

  @Test
  @DisplayName("凭据不存在（撤销改邮箱后）：做一次假哈希，按失败结算，401")
  void should_use_dummy_hash_when_credential_is_missing() {
    when(credentials.findByUserId(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("any")))
        .isInstanceOf(CurrentPasswordMismatchException.class);
    verify(passwordHashing).verifyAgainstDummy(any());
    verify(passwordHashing, never()).matches(any(), any());
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("宽限期内再认证：不换令牌，结果里没有新令牌（门户保持现有 Cookie），账号信息照常")
  void should_keep_token_when_authenticated_within_grace() {
    when(sessions.rotate(AccountType.USER, 42L, 7001L, Duration.ofSeconds(10)))
        .thenReturn(Optional.of(SessionRotation.kept()));

    Reauthenticated result = handler.handle(ReauthenticateUserCommand.of("Correct-Horse-02"));

    assertThat(result.sessionToken()).isNull();
    assertThat(result.account()).isEqualTo(ACCOUNT);
    verify(loginThrottle).recordSuccess(ATTEMPT);
  }

  @Test
  @DisplayName("换令牌时会话已不在：401")
  void should_require_login_when_session_vanished_during_rotation() {
    when(sessions.rotate(AccountType.USER, 42L, 7001L, Duration.ofSeconds(10)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("Correct-Horse-02")))
        .isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  @DisplayName("查凭据时出错：按取消结算，不计失败，异常原样抛出")
  void should_cancel_attempt_when_lookup_fails() {
    IllegalStateException boom = new IllegalStateException("db");
    when(credentials.findByUserId(42L)).thenThrow(boom);

    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("any"))).isSameAs(boom);
    verify(loginThrottle).cancel(ATTEMPT);
    verify(loginThrottle, never()).recordFailure(any());
  }

  @Test
  @DisplayName("读账号失败时不换令牌：旧令牌仍然可用")
  void should_not_rotate_when_account_lookup_fails() {
    when(userReadPort.findAccount(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(ReauthenticateUserCommand.of("Correct-Horse-02")))
        .isInstanceOf(AuthenticationRequiredException.class);
    verify(sessions, never())
        .rotate(
            any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            any());
  }

  @Test
  @DisplayName("命令的 toString 不输出密码")
  void should_hide_password_in_command_to_string() {
    assertThat(ReauthenticateUserCommand.of("Correct-Horse-02").toString())
        .doesNotContain("Correct-Horse-02");
  }

  /// 指定状态的用户 42。
  ///
  /// @param status 状态
  /// @return 用户
  private static User user(UserStatus status) {
    User.UserBuilder builder =
        User.restore().id(42L).username(Username.of("chen-yu")).status(status).version(0L);
    if (status != UserStatus.DELETED) {
      builder.email(EMAIL);
    }
    return switch (status) {
      case BANNED -> builder.bannedAt(Instant.EPOCH).build();
      case PENDING_DELETION -> builder.deletionDueAt(Instant.EPOCH).build();
      case DELETED -> builder.deletedAt(Instant.EPOCH).build();
      case ACTIVE -> builder.build();
    };
  }
}
