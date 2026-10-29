package dev.patra.identity.app.account.usecase.login;

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

import dev.patra.identity.domain.account.exception.InvalidCredentialsException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.exception.UserBannedException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.NewDeviceLoginMail;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.LoginAttempt;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.policy.NewDeviceLoginPolicy;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.throttle.NewDeviceMailThrottlePort;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AccountType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// LoginUserHandler 单元测试。
@DisplayName("LoginUserHandler 单元测试")
class LoginUserHandlerTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final Username USERNAME = Username.of("chen-yu");
  private static final User ACTIVE_USER =
      User.restore()
          .id(42L)
          .email(EMAIL)
          .username(USERNAME)
          .status(UserStatus.ACTIVE)
          .version(0L)
          .build();
  private static final User BANNED_USER =
      User.restore()
          .id(42L)
          .email(EMAIL)
          .username(USERNAME)
          .status(UserStatus.BANNED)
          .bannedAt(Instant.parse("2026-10-06T08:00:00Z"))
          .version(1L)
          .build();
  private static final User PENDING_USER =
      User.restore()
          .id(42L)
          .email(EMAIL)
          .username(USERNAME)
          .status(UserStatus.PENDING_DELETION)
          .deletionDueAt(Instant.parse("2026-11-04T08:00:00Z"))
          .version(1L)
          .build();
  private static final User DELETED_USER =
      User.restore()
          .id(42L)
          .username(USERNAME)
          .status(UserStatus.DELETED)
          .deletedAt(Instant.parse("2026-11-04T08:00:00Z"))
          .version(2L)
          .build();
  private static final UserPasswordCredential CREDENTIAL =
      UserPasswordCredential.restore(7L, 42L, HASH, 0L);
  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final String DEVICE = "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";
  private static final NewDeviceLoginPolicy NEW_DEVICE_POLICY =
      NewDeviceLoginPolicy.of(Duration.ofDays(180), Duration.ofHours(24));
  private static final String UA = "Mozilla/5.0 (Macintosh) Chrome/141.0.0.0";
  private static final ClientOrigin ORIGIN =
      ClientOrigin.builder()
          .ip("203.0.113.9")
          .userAgent(UA)
          .deviceName("Chrome 141 · macOS")
          .deviceClass(DeviceClass.DESKTOP)
          .ipRegion("澳大利亚")
          .build();
  private static final LoginAttempt ATTEMPT = LoginAttempt.of(AccountType.USER, EMAIL, "ticket");

  private static final UserAccountReadModel ACCOUNT =
      UserAccountReadModel.builder()
          .userId(42L)
          .email("chen.yu@example.com")
          .emailVerified(false)
          .username("chen-yu")
          .displayName("chen-yu")
          .status(UserStatus.ACTIVE)
          .build();

  private final UserRepository users = mock(UserRepository.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final LoginThrottlePort loginThrottle = mock(LoginThrottlePort.class);
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final ClientOriginResolver resolver = mock(ClientOriginResolver.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final NewDeviceMailThrottlePort newDeviceThrottle = mock(NewDeviceMailThrottlePort.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final LoginUserHandler handler =
      new LoginUserHandler(
          users,
          credentials,
          userReadPort,
          passwordHashing,
          loginThrottle,
          sessionIssuer,
          resolver,
          records,
          NEW_DEVICE_POLICY,
          newDeviceThrottle,
          emailDelivery,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 默认放行，用户和凭据都存在。
  @BeforeEach
  void stubHappyPath() {
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenReturn(ATTEMPT);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ACTIVE_USER));
    when(credentials.findByUserId(42L)).thenReturn(Optional.of(CREDENTIAL));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(ACTIVE_USER));
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.empty());
    when(sessionIssuer.issue(anyLong(), any(), any()))
        .thenReturn(IssuedUserSession.of("patra_user_x", List.of()));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
    when(resolver.resolve(any())).thenReturn(ORIGIN);
  }

  @Test
  @DisplayName("密码正确：按成功结算，返回用户 ID 和邮箱")
  void should_authenticate_with_correct_password() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    AuthenticatedUser result =
        handler.handle(command("Chen.Yu@Example.com", "correct horse", null, null));

    assertThat(result.account().userId()).isEqualTo(42L);
    assertThat(result.account().email()).isEqualTo("chen.yu@example.com");
    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    verify(loginThrottle).recordSuccess(ATTEMPT);
    verify(loginThrottle, never()).recordFailure(any());
    verify(loginThrottle, never()).cancel(any());
  }

  @Test
  @DisplayName("密码错误：按失败结算，返回 401")
  void should_reject_wrong_password() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "wrong", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("这次失败触发上锁：返回 429 和锁定时长")
  void should_return_429_when_failure_triggers_lock() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.of(Duration.ofMinutes(15)));

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "wrong", null, null)))
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

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "any", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).verifyAgainstDummy(any());
    verify(passwordHashing, never()).matches(any(), any());
    verify(loginThrottle).recordFailure(ATTEMPT);
  }

  @Test
  @DisplayName("用户在但凭据不在：也按密码错处理")
  void should_treat_missing_credential_like_wrong_password() {
    when(credentials.findByUserId(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "any", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).verifyAgainstDummy(any());
  }

  @Test
  @DisplayName("锁定期间直接返回 429，不查库、不做哈希")
  void should_reject_when_locked_without_touching_storage() {
    when(loginThrottle.begin(AccountType.USER, EMAIL))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofMinutes(10)));

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "any", null, null)))
        .isInstanceOf(LoginTemporarilyLockedException.class);
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("封禁的账号：密码对返回 403")
  void should_return_403_for_banned_user_with_correct_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "right", null, null)))
        .isInstanceOf(UserBannedException.class);
    verify(loginThrottle).recordSuccess(ATTEMPT);
  }

  @Test
  @DisplayName("封禁的账号：密码错仍是 401，不暴露封禁")
  void should_return_401_for_banned_user_with_wrong_password() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "wrong", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("哈希排队超时：按取消结算，不计失败，503 原样传出")
  void should_cancel_attempt_when_hashing_is_unavailable() {
    TemporarilyUnavailableException busy = new TemporarilyUnavailableException();
    when(passwordHashing.matches(any(), any())).thenThrow(busy);

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "any", null, null)))
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

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "any", null, null)))
        .isSameAs(busy);
  }

  @Test
  @DisplayName("字段为 null 时报 REQUIRED，不进失败限制")
  void should_validate_fields_before_throttling() {
    assertThatThrownBy(() -> handler.handle(command(null, null, null, null)))
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

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "abc", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(passwordHashing).matches(any(), any());
  }

  @Test
  @DisplayName("超过登录长度上限的密码：不查库、不做哈希，按失败结算，返回 401")
  void should_reject_overlong_password_without_hashing() {
    String overlong = "x".repeat(PasswordPolicy.MAX_LOGIN_LENGTH + 1);

    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", overlong, null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("长度正好在登录上限（按码点算）的密码照常校验")
  void should_verify_password_at_login_length_limit() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    handler.handle(
        command("chen.yu@example.com", "😀".repeat(PasswordPolicy.MAX_LOGIN_LENGTH), null, null));

    verify(passwordHashing).matches(any(), any());
  }

  @Test
  @DisplayName("1 MB 的邮箱：按字段校验报 TOO_LONG，不进失败限制")
  void should_reject_huge_email_by_field_validation() {
    String hugeEmail = "a".repeat(1_000_000) + "@example.com";

    assertThatThrownBy(() -> handler.handle(command(hugeEmail, "any", null, null)))
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
    assertThat(command("chen.yu@example.com", "Secret-Value-1", null, null).toString())
        .doesNotContain("Secret-Value-1");
  }

  @Test
  @DisplayName("凭据正确：在结算成功之后、以已校验的用户 ID 和客户端信息建会话")
  void should_issue_session_after_successful_authentication() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    AuthenticatedUser result =
        handler.handle(
            command(
                "chen.yu@example.com",
                "correct horse",
                "web",
                "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy"));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    ArgumentCaptor<LoginClient> client = ArgumentCaptor.forClass(LoginClient.class);
    verify(sessionIssuer).issue(eq(42L), client.capture(), any());
    assertThat(client.getValue().device())
        .map(DeviceId::value)
        .contains("Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy");
    var order = inOrder(loginThrottle, sessionIssuer);
    order.verify(loginThrottle).recordSuccess(ATTEMPT);
    order.verify(sessionIssuer).issue(anyLong(), any(), any());
  }

  @Test
  @DisplayName("密码错误、账号被封禁：都不建会话")
  void should_not_issue_session_when_rejected() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);
    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "wrong", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);

    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(BANNED_USER));
    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "correct", null, null)))
        .isInstanceOf(UserBannedException.class);

    verifyNoInteractions(sessionIssuer);
  }

  @Test
  @DisplayName("客户端类型和设备标识的错误和邮箱、密码一起报，不碰限流")
  void should_report_client_violations_with_other_fields() {
    assertThatThrownBy(() -> handler.handle(command("", "", "app", "x".repeat(129))))
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
    assertThat(command("a@example.com", "secret", "web", null).toString())
        .doesNotContain("secret")
        .contains("a@example.com");
    assertThat(AuthenticatedUser.of("patra_user_x", ACCOUNT).toString())
        .doesNotContain("patra_user_x")
        .contains("42");
  }

  @Test
  @DisplayName("密码对但建会话前用户已被封禁（事务内加锁重读到 BANNED）：403，不建会话")
  void should_reject_when_user_was_banned_before_session_is_issued() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(BANNED_USER));

    assertThatThrownBy(
            () -> handler.handle(command("chen.yu@example.com", "correct horse", null, null)))
        .isInstanceOf(UserBannedException.class);
    verify(loginThrottle).recordSuccess(ATTEMPT);
    verify(sessionIssuer, never()).issue(anyLong(), any(), any());
  }

  @Test
  @DisplayName("建会话前用户已不存在：按凭据错误处理，不建会话")
  void should_reject_when_user_vanished_before_session_is_issued() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> handler.handle(command("chen.yu@example.com", "correct horse", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(sessionIssuer, never()).issue(anyLong(), any(), any());
  }

  @Test
  @DisplayName("加锁重读和建会话在同一个事务里，哈希在事务外")
  void should_recheck_ban_with_lock_inside_the_same_transaction_as_issuing() {
    AtomicBoolean inTransaction = new AtomicBoolean(false);
    TransactionOperations recording =
        new TransactionOperations() {
          /// 标记事务边界后执行回调。
          ///
          /// @param action 回调
          /// @param <T> 结果类型
          /// @return 回调结果
          @Override
          public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            inTransaction.set(true);
            try {
              return action.doInTransaction(new SimpleTransactionStatus());
            } finally {
              inTransaction.set(false);
            }
          }
        };
    LoginUserHandler transactional =
        new LoginUserHandler(
            users,
            credentials,
            userReadPort,
            passwordHashing,
            loginThrottle,
            sessionIssuer,
            resolver,
            records,
            NEW_DEVICE_POLICY,
            newDeviceThrottle,
            emailDelivery,
            recording,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(passwordHashing.matches(any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isFalse();
              return true;
            });
    when(users.findByIdForUpdate(42L))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return Optional.of(ACTIVE_USER);
            });
    when(sessionIssuer.issue(anyLong(), any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return IssuedUserSession.of("patra_user_x", List.of());
            });

    transactional.handle(command("chen.yu@example.com", "correct horse", null, null));

    verify(users).findByIdForUpdate(42L);
    verify(sessionIssuer).issue(eq(42L), any(), any());
  }

  @Test
  @DisplayName("已注销的用户：密码匹配也按失败结算、401，不记成功、不建会话")
  void should_settle_deleted_user_as_failure_without_recording_success() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(DELETED_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    assertThatThrownBy(
            () -> handler.handle(command("chen.yu@example.com", "correct horse", null, null)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verify(loginThrottle, never()).recordSuccess(any());
    verifyNoInteractions(sessionIssuer);
  }

  @Test
  @DisplayName("冷静期内的用户照常登录建会话")
  void should_login_pending_deletion_user() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(PENDING_USER));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(PENDING_USER));
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    AuthenticatedUser result =
        handler.handle(command("chen.yu@example.com", "correct horse", null, null));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    verify(loginThrottle).recordSuccess(ATTEMPT);
    verify(sessionIssuer).issue(eq(42L), any(), any());
  }

  @Test
  @DisplayName("请求来源在事务之前解析，登录记录带上它")
  void should_resolve_origin_outside_transaction_and_record_it() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);

    handler.handle(
        LoginUserCommand.builder()
            .email("chen.yu@example.com")
            .password("correct horse")
            .clientIp("203.0.113.9")
            .userAgent(UA)
            .build());

    InOrder order = inOrder(resolver, users, sessionIssuer);
    order.verify(resolver).resolve(RequestOrigin.of("203.0.113.9", UA));
    order.verify(users).findByIdForUpdate(42L);
    order.verify(sessionIssuer).issue(eq(42L), any(), eq(ORIGIN));
  }

  @Test
  @DisplayName("没见过的设备：在事务里、建会话之前判定，提交后取限流并发新设备邮件")
  void should_mail_when_device_not_seen() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(records.existsTrustedDevice(eq(42L), any(), any())).thenReturn(false);
    when(newDeviceThrottle.acquire(eq(42L), any(), any())).thenReturn(true);
    when(emailDelivery.send(any())).thenReturn(true);

    handler.handle(command("chen.yu@example.com", "correct horse", "web", DEVICE));

    verify(newDeviceThrottle, never()).reset(anyLong(), any());
    InOrder order = inOrder(records, sessionIssuer, newDeviceThrottle, emailDelivery);
    order
        .verify(records)
        .existsTrustedDevice(42L, new DeviceId(DEVICE), NOW.minus(Duration.ofDays(180)));
    order.verify(sessionIssuer).issue(eq(42L), any(), any());
    order.verify(newDeviceThrottle).acquire(42L, new DeviceId(DEVICE), NOW);
    ArgumentCaptor<OutgoingMail> mail = ArgumentCaptor.forClass(OutgoingMail.class);
    order.verify(emailDelivery).send(mail.capture());
    assertThat(mail.getValue()).isInstanceOf(NewDeviceLoginMail.class);
    NewDeviceLoginMail sent = (NewDeviceLoginMail) mail.getValue();
    assertThat(sent.to()).isEqualTo(EMAIL);
    assertThat(sent.notice().device()).isEqualTo("Chrome 141 · macOS");
    assertThat(sent.notice().ipAddress()).isEqualTo("203.0.113.9");
    assertThat(sent.notice().ipLocation()).isEqualTo("澳大利亚");
    assertThat(sent.notice().occurredAt()).isEqualTo(NOW);
    assertThat(sent.notice().notMeAdvice()).isEqualTo(NewDeviceLoginMail.NOT_ME_ADVICE);
  }

  @Test
  @DisplayName("发信被拒（队列满、已停机）：退还这台设备的冷却，下次登录还能提醒")
  void should_release_cooldown_when_mail_rejected() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(records.existsTrustedDevice(eq(42L), any(), any())).thenReturn(false);
    when(newDeviceThrottle.acquire(eq(42L), any(), any())).thenReturn(true);
    when(emailDelivery.send(any())).thenReturn(false);

    handler.handle(command("chen.yu@example.com", "correct horse", "web", DEVICE));

    InOrder order = inOrder(emailDelivery, newDeviceThrottle);
    order.verify(emailDelivery).send(any(NewDeviceLoginMail.class));
    order.verify(newDeviceThrottle).reset(42L, new DeviceId(DEVICE));
  }

  @Test
  @DisplayName("发信被拒且退还冷却时 Redis 不可用：登录照常成功")
  void should_still_log_in_when_cooldown_release_fails() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(records.existsTrustedDevice(eq(42L), any(), any())).thenReturn(false);
    when(newDeviceThrottle.acquire(eq(42L), any(), any())).thenReturn(true);
    when(emailDelivery.send(any())).thenReturn(false);
    doThrow(new TemporarilyUnavailableException()).when(newDeviceThrottle).reset(eq(42L), any());

    AuthenticatedUser result =
        handler.handle(command("chen.yu@example.com", "correct horse", "web", DEVICE));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
  }

  @Test
  @DisplayName("见过的设备：不取限流、不发信")
  void should_not_mail_when_device_seen() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(records.existsTrustedDevice(eq(42L), any(), any())).thenReturn(true);

    handler.handle(command("chen.yu@example.com", "correct horse", "web", DEVICE));

    verifyNoInteractions(newDeviceThrottle, emailDelivery);
  }

  @Test
  @DisplayName("冷却期内：不发信")
  void should_not_mail_when_throttled() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(records.existsTrustedDevice(eq(42L), any(), any())).thenReturn(false);
    when(newDeviceThrottle.acquire(eq(42L), any(), any())).thenReturn(false);

    handler.handle(command("chen.yu@example.com", "correct horse", "web", DEVICE));

    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("没带设备标识：不查记录，按新设备走「无设备」限流桶")
  void should_treat_missing_device_id_as_new() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(newDeviceThrottle.acquire(eq(42L), any(), any())).thenReturn(true);

    handler.handle(command("chen.yu@example.com", "correct horse", "web", null));

    verify(records, never()).existsTrustedDevice(anyLong(), any(), any());
    verify(newDeviceThrottle).acquire(42L, null, NOW);
    verify(emailDelivery).send(any(NewDeviceLoginMail.class));
  }

  @Test
  @DisplayName("限流的 Redis 不可用：登录照常成功，只是不发信")
  void should_still_log_in_when_throttle_unavailable() {
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(newDeviceThrottle.acquire(eq(42L), any(), any()))
        .thenThrow(new TemporarilyUnavailableException());

    AuthenticatedUser result =
        handler.handle(command("chen.yu@example.com", "correct horse", "web", null));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    verifyNoInteractions(emailDelivery);
  }

  /// 只带四个基本字段的登录命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @param clientType 客户端类型
  /// @param deviceId 设备标识
  /// @return 命令
  private static LoginUserCommand command(
      String email, String password, String clientType, String deviceId) {
    return LoginUserCommand.builder()
        .email(email)
        .password(password)
        .clientType(clientType)
        .deviceId(deviceId)
        .build();
  }
}
