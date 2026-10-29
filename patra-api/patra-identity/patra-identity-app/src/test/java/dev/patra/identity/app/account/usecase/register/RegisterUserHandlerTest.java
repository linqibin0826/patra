package dev.patra.identity.app.account.usecase.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.EmailVerificationMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.policy.UsernamePolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.EmailResendThrottlePort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.tool.core.error.field.FieldViolation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// RegisterUserHandler 单元测试。
@DisplayName("RegisterUserHandler 单元测试")
class RegisterUserHandlerTest {

  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final Username USERNAME = Username.of("chen-yu");
  private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
  private static final User SAVED_USER =
      User.restore()
          .id(42L)
          .email(EMAIL)
          .username(USERNAME)
          .status(UserStatus.ACTIVE)
          .version(0L)
          .build();

  private static final UserAccountReadModel ACCOUNT =
      UserAccountReadModel.builder()
          .userId(42L)
          .email("chen.yu@example.com")
          .emailVerified(false)
          .username("chen-yu")
          .displayName("chen-yu")
          .status(UserStatus.ACTIVE)
          .build();

  private static final EmailTokenSecret SECRET = new EmailTokenSecret("E".repeat(43));
  private static final IssuedEmailToken ISSUED =
      IssuedEmailToken.of(
          EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW, null), SECRET);
  private static final MailLinkPolicy LINKS =
      MailLinkPolicy.builder()
          .baseUrl("https://patra.example")
          .verifyEmailPath("/verify-email")
          .securitySettingsPath("/settings/security")
          .resetPasswordPath("/reset-password")
          .confirmEmailChangePath("/confirm-email-change")
          .revertEmailChangePath("/revert-email-change")
          .build();
  private static final EmailVerificationMail EXPECTED_MAIL =
      EmailVerificationMail.of(
          EMAIL, "chen-yu", LINKS.verifyEmailUrl(SECRET), NOW.plus(Duration.ofHours(24)));

  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final FormerUsernameRepository formerUsernames = mock(FormerUsernameRepository.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final PasswordPolicy passwordPolicy = new PasswordPolicy(Set.of("password123")::contains);
  private final UsernamePolicy usernamePolicy = new UsernamePolicy();
  private static final String UA = "Mozilla/5.0 (Macintosh) Chrome/141.0.0.0";
  private static final ClientOrigin ORIGIN =
      ClientOrigin.builder()
          .ip("203.0.113.9")
          .userAgent(UA)
          .deviceName("Chrome 141 · macOS")
          .deviceClass(DeviceClass.DESKTOP)
          .build();
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final ClientOriginResolver resolver = mock(ClientOriginResolver.class);
  private final EmailTokenIssuer emailTokenIssuer = mock(EmailTokenIssuer.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final EmailResendThrottlePort emailResendThrottle = mock(EmailResendThrottlePort.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final RegisterUserHandler handler = handler(TransactionOperations.withoutTransaction());

  /// 按给定的事务操作建处理器。
  ///
  /// @param transactions 事务操作
  /// @return 处理器
  private RegisterUserHandler handler(TransactionOperations transactions) {
    return new RegisterUserHandler(
        users,
        profiles,
        credentials,
        formerUsernames,
        userReadPort,
        passwordHashing,
        passwordPolicy,
        usernamePolicy,
        sessionIssuer,
        resolver,
        emailTokenIssuer,
        LINKS,
        emailDelivery,
        emailResendThrottle,
        transactions,
        clock);
  }

  /// 执行回调期间把标记置真的事务操作，用来断言哪些调用在事务里、哪些在事务外。
  ///
  /// @param inTransaction 标记
  /// @return 事务操作
  private static TransactionOperations recording(AtomicBoolean inTransaction) {
    return new TransactionOperations() {
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
  }

  /// 成功路径的桩。
  private void stubHappyPath() {
    when(passwordHashing.hash(any())).thenReturn(HASH);
    when(users.save(any())).thenReturn(SAVED_USER);
    when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(credentials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessionIssuer.issue(anyLong(), any(), any()))
        .thenReturn(IssuedUserSession.of("patra_user_x", List.of()));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any())).thenReturn(ISSUED);
    when(resolver.resolve(any())).thenReturn(ORIGIN);
  }

  /// 带邮箱、用户名、密码的命令。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @param password 密码
  /// @return 命令
  private static RegisterUserCommand command(String email, String username, String password) {
    return RegisterUserCommand.builder().email(email).username(username).password(password).build();
  }

  @Test
  @DisplayName("注册成功：存用户、存凭据，返回用户 ID 和规范化之后的邮箱；用户名也规范化")
  void should_register_user_with_password_credential() {
    stubHappyPath();

    AuthenticatedUser result =
        handler.handle(command(" Chen.Yu@Example.com ", " Chen-Yu ", "correct horse battery"));

    assertThat(result.account().userId()).isEqualTo(42L);
    assertThat(result.account().email()).isEqualTo("chen.yu@example.com");
    assertThat(result.account().username()).isEqualTo("chen-yu");
    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    verify(users).existsByEmail(EMAIL);
    verify(users).existsByUsername(USERNAME);
    verify(formerUsernames).existsReserved(USERNAME, NOW);
    ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
    verify(users).save(user.capture());
    assertThat(user.getValue().getUsername()).isEqualTo(USERNAME);
    ArgumentCaptor<UserPasswordCredential> credential =
        ArgumentCaptor.forClass(UserPasswordCredential.class);
    verify(credentials).save(credential.capture());
    assertThat(credential.getValue().getUserId()).isEqualTo(42L);
    assertThat(credential.getValue().getPasswordHash()).isEqualTo(HASH);
  }

  @Test
  @DisplayName("注册时在同一个事务里建一份空资料：昵称为空，跟随用户名")
  void should_create_empty_profile_in_the_transaction() {
    stubHappyPath();

    handler.handle(command("chen.yu@example.com", "chen-yu", "correct horse battery"));

    ArgumentCaptor<UserProfile> profile = ArgumentCaptor.forClass(UserProfile.class);
    verify(profiles).save(profile.capture());
    assertThat(profile.getValue().getUserId()).isEqualTo(42L);
    assertThat(profile.getValue().getDisplayName()).isEmpty();
    InOrder order = inOrder(users, profiles, credentials, sessionIssuer);
    order.verify(users).save(any());
    order.verify(profiles).save(any());
    order.verify(credentials).save(any());
    order.verify(sessionIssuer).issue(eq(42L), any(), any());
  }

  @Test
  @DisplayName("五个字段的错误一次报全，不查库也不做哈希")
  void should_report_all_five_field_violations_at_once() {
    RegisterUserCommand command =
        RegisterUserCommand.builder()
            .email("")
            .username("ab")
            .password("short")
            .clientType("tv")
            .deviceId("a" + (char) 1 + "b")
            .build();

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly(
                        "email:REQUIRED",
                        "username:TOO_SHORT",
                        "password:TOO_SHORT",
                        "clientType:INVALID_FORMAT",
                        "deviceId:INVALID_FORMAT"));
    verifyNoInteractions(users, profiles, credentials, formerUsernames, passwordHashing);
  }

  @Test
  @DisplayName("字段为 null 时按 REQUIRED 处理，不出空指针")
  void should_treat_null_fields_as_required() {
    assertThatThrownBy(() -> handler.handle(RegisterUserCommand.builder().build()))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:REQUIRED", "username:REQUIRED", "password:REQUIRED"));
  }

  @Test
  @DisplayName("常见密码报 TOO_COMMON，保留字报 RESERVED")
  void should_reject_common_password_and_reserved_username() {
    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "admin", "Password123")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("username:RESERVED", "password:TOO_COMMON"));
  }

  @Test
  @DisplayName("1 MB 的邮箱、用户名和密码在长度校验处拒绝，不做哈希")
  void should_reject_huge_input_without_hashing() {
    String hugeEmail = "a".repeat(1_000_000) + "@example.com";
    String hugeUsername = "u".repeat(1_000_000);
    String hugePassword = "b".repeat(1_000_000);

    assertThatThrownBy(() -> handler.handle(command(hugeEmail, hugeUsername, hugePassword)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:TOO_LONG", "username:TOO_LONG", "password:TOO_LONG"));
    verifyNoInteractions(passwordHashing);
  }

  @Test
  @DisplayName("邮箱和用户名同时被占用：一个 409 带两条字段错误，不做哈希、不保存")
  void should_report_both_taken_identifiers_in_one_409() {
    when(users.existsByEmail(EMAIL)).thenReturn(true);
    when(users.existsByUsername(USERNAME)).thenReturn(true);

    assertThatThrownBy(
            () ->
                handler.handle(command("CHEN.YU@example.com", "chen-yu", "correct horse battery")))
        .isInstanceOfSatisfying(
            UserIdentifierTakenException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .extracting(FieldViolation::field)
                    .containsExactly("email", "username"));
    verifyNoInteractions(passwordHashing);
    verify(users, never()).save(any());
    verifyNoInteractions(profiles, credentials);
  }

  @Test
  @DisplayName("用户名是别人保留期内的旧名：按已被使用处理，409 只有 username 一条")
  void should_treat_reserved_former_username_as_taken() {
    when(users.existsByUsername(USERNAME)).thenReturn(false);
    when(formerUsernames.existsReserved(USERNAME, NOW)).thenReturn(true);

    assertThatThrownBy(
            () ->
                handler.handle(command("chen.yu@example.com", "chen-yu", "correct horse battery")))
        .isInstanceOfSatisfying(
            UserIdentifierTakenException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .singleElement()
                    .satisfies(
                        v -> {
                          assertThat(v.field()).isEqualTo("username");
                          assertThat(v.code()).isEqualTo("ALREADY_TAKEN");
                        }));
    verifyNoInteractions(passwordHashing);
  }

  @Test
  @DisplayName("哈希在事务外做，三次保存和建会话在同一个事务里")
  void should_hash_outside_transaction_and_save_inside() {
    AtomicBoolean inTransaction = new AtomicBoolean(false);
    RegisterUserHandler transactional = handler(recording(inTransaction));
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any())).thenReturn(ISSUED);
    when(passwordHashing.hash(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isFalse();
              return HASH;
            });
    when(users.save(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return SAVED_USER;
            });
    when(profiles.save(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return invocation.getArgument(0);
            });
    when(credentials.save(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return invocation.getArgument(0);
            });
    when(sessionIssuer.issue(anyLong(), any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return IssuedUserSession.of("patra_user_x", List.of());
            });
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));

    transactional.handle(command("chen.yu@example.com", "chen-yu", "correct horse battery"));

    verify(profiles).save(any());
    verify(credentials).save(any());
    verify(sessionIssuer).issue(eq(42L), any(), any());
  }

  @Test
  @DisplayName("并发注册撞上唯一约束时，仓储抛出的 409 原样传出，不再存资料和凭据")
  void should_propagate_unique_violation() {
    when(passwordHashing.hash(any())).thenReturn(HASH);
    when(users.save(any()))
        .thenThrow(
            new UserIdentifierTakenException(List.of(UserFieldViolations.usernameAlreadyTaken())));

    assertThatThrownBy(
            () ->
                handler.handle(command("chen.yu@example.com", "chen-yu", "correct horse battery")))
        .isInstanceOf(UserIdentifierTakenException.class);
    verifyNoInteractions(profiles, credentials, sessionIssuer);
  }

  @Test
  @DisplayName("命令的 toString 不输出密码")
  void should_hide_password_in_command_to_string() {
    assertThat(command("chen.yu@example.com", "chen-yu", "Secret-Value-1").toString())
        .doesNotContain("Secret-Value-1")
        .contains("chen-yu");
  }

  @Test
  @DisplayName("注册成功后以新用户的 ID 和客户端信息建会话")
  void should_issue_session_for_new_user() {
    stubHappyPath();

    AuthenticatedUser result =
        handler.handle(
            RegisterUserCommand.builder()
                .email("chen.yu@example.com")
                .username("chen-yu")
                .password("correct horse battery")
                .clientType("web")
                .deviceId("Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy")
                .build());

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    ArgumentCaptor<LoginClient> client = ArgumentCaptor.forClass(LoginClient.class);
    verify(sessionIssuer).issue(eq(42L), client.capture(), any());
    assertThat(client.getValue().device())
        .map(DeviceId::value)
        .contains("Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy");
  }

  @Test
  @DisplayName("邮箱已注册或字段不合法：不建会话")
  void should_not_issue_session_when_registration_is_rejected() {
    when(users.existsByEmail(EMAIL)).thenReturn(true);
    assertThatThrownBy(
            () ->
                handler.handle(command("chen.yu@example.com", "chen-yu", "correct horse battery")))
        .isInstanceOf(UserIdentifierTakenException.class);
    assertThatThrownBy(() -> handler.handle(command("chen.yu@example.com", "chen-yu", "short")))
        .isInstanceOf(InvalidUserFieldsException.class);

    verifyNoInteractions(sessionIssuer);
  }

  @Test
  @DisplayName("注册成功：令牌在事务里签发，提交后先计数（不带 IP）再发信；IP 交给签发；发信端口收到签发返回的邮件")
  void should_issue_verification_token_inside_transaction_and_send_after_commit() {
    AtomicBoolean inTransaction = new AtomicBoolean(false);
    RegisterUserHandler transactional = handler(recording(inTransaction));
    stubHappyPath();
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return ISSUED;
            });
    doAnswer(
            invocation -> {
              assertThat(inTransaction).isFalse();
              return null;
            })
        .when(emailResendThrottle)
        .markSent(anyLong(), any());
    doAnswer(
            invocation -> {
              assertThat(inTransaction).isFalse();
              return null;
            })
        .when(emailDelivery)
        .send(any());

    transactional.handle(
        RegisterUserCommand.builder()
            .email("chen.yu@example.com")
            .username("chen-yu")
            .password("correct horse battery")
            .clientIp("203.0.113.9")
            .build());

    InOrder order = inOrder(sessionIssuer, emailTokenIssuer, emailResendThrottle, emailDelivery);
    order.verify(sessionIssuer).issue(eq(42L), any(), any());
    order
        .verify(emailTokenIssuer)
        .issue(eq(EmailTokenPurpose.EMAIL_VERIFY), eq(42L), eq(EMAIL), eq("203.0.113.9"));
    order.verify(emailResendThrottle).markSent(42L, NOW);
    order.verify(emailDelivery).send(EXPECTED_MAIL);
  }

  @Test
  @DisplayName("没有客户端 IP 也能注册：签发时 IP 为 null")
  void should_issue_without_client_ip() {
    stubHappyPath();

    handler.handle(command("chen.yu@example.com", "chen-yu", "correct horse battery"));

    verify(emailTokenIssuer)
        .issue(eq(EmailTokenPurpose.EMAIL_VERIFY), eq(42L), eq(EMAIL), isNull());
    verify(emailDelivery).send(EXPECTED_MAIL);
  }

  @Test
  @DisplayName("计数失败（Redis 不可用）不影响注册：照样 201、照样发信")
  void should_still_register_and_send_when_throttle_marking_fails() {
    stubHappyPath();
    doThrow(new TemporarilyUnavailableException())
        .when(emailResendThrottle)
        .markSent(anyLong(), any());

    AuthenticatedUser result =
        handler.handle(command("chen.yu@example.com", "chen-yu", "correct horse battery"));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    verify(emailDelivery).send(EXPECTED_MAIL);
  }

  @Test
  @DisplayName("请求来源在事务之前解析，注册建的登录记录带上它")
  void should_resolve_origin_outside_transaction_and_record_it() {
    stubHappyPath();

    handler.handle(
        RegisterUserCommand.builder()
            .email("chen.yu@example.com")
            .username("chen-yu")
            .password("correct horse battery")
            .clientIp("203.0.113.9")
            .userAgent(UA)
            .build());

    InOrder order = inOrder(resolver, users, sessionIssuer);
    order.verify(resolver).resolve(RequestOrigin.of("203.0.113.9", UA));
    order.verify(users).save(any());
    order.verify(sessionIssuer).issue(eq(42L), any(), eq(ORIGIN));
  }
}
