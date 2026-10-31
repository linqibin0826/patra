package dev.patra.identity.app.account.usecase.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.PasswordResetLimitedException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetDoneMail;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.account.port.throttle.PasswordResetThrottlePort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AccountType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// ResetPasswordHandler 单元测试：提交限流、密码不合规不消费令牌、令牌检查、重设的顺序。
@DisplayName("ResetPasswordHandler 单元测试")
class ResetPasswordHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("S".repeat(43));
  private static final PasswordHash NEW_HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$bmV3aGFzaA");
  private static final PasswordHash OLD_HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final UserAccountReadModel ACCOUNT =
      UserAccountReadModel.builder()
          .userId(42L)
          .email("chen.yu@example.com")
          .emailVerified(true)
          .username("chen-yu")
          .displayName("陈宇")
          .status(UserStatus.ACTIVE)
          .build();

  private final PasswordResetThrottlePort passwordResetThrottle =
      mock(PasswordResetThrottlePort.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final LoginThrottlePort loginThrottle = mock(LoginThrottlePort.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final ClientOriginResolver resolver =
      new ClientOriginResolver(
          userAgent -> DeviceProfile.of("Chrome 141 · macOS", DeviceClass.DESKTOP),
          ip -> Optional.of("中国 广东 深圳"));
  private final AtomicBoolean inTransaction = new AtomicBoolean(false);
  private final ResetPasswordHandler handler =
      new ResetPasswordHandler(
          passwordResetThrottle,
          new PasswordPolicy(Set.of("password123")::contains),
          passwordHashing,
          tokens,
          users,
          credentials,
          userReadPort,
          new SessionTerminator(sessions, records),
          loginThrottle,
          resolver,
          emailDelivery,
          RequestPasswordResetHandlerTest.recording(inTransaction),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(passwordResetThrottle.acquireSubmit(any(), any())).thenReturn(Optional.empty());
    when(passwordHashing.hash(any())).thenReturn(NEW_HASH);
    when(tokens.findByTokenHash(SECRET.hash()))
        .thenReturn(Optional.of(token(EmailTokenPurpose.PASSWORD_RESET, EMAIL)));
    when(tokens.consume(SECRET.hash(), EmailTokenPurpose.PASSWORD_RESET, NOW))
        .thenReturn(Optional.of(token(EmailTokenPurpose.PASSWORD_RESET, EMAIL)));
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE, EMAIL)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(credentials.findByUserId(42L))
        .thenAnswer(
            invocation -> Optional.of(UserPasswordCredential.restore(7L, 42L, OLD_HASH, 0L)));
    when(credentials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of(7001L));
    when(records.findAllByIds(List.of(7001L)))
        .thenReturn(
            List.of(
                UserLoginRecord.start(
                    42L,
                    LoginClient.of("web", null),
                    ClientOrigin.builder().deviceClass(DeviceClass.UNKNOWN).build(),
                    NOW.plusSeconds(60))));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
  }

  @Test
  @DisplayName("提交限流先于一切：429，不碰令牌")
  void should_throttle_submit_before_anything() {
    when(passwordResetThrottle.acquireSubmit("127.0.0.1", NOW))
        .thenReturn(Optional.of(Duration.ofSeconds(30)));

    assertThatThrownBy(
            () ->
                handler.handle(
                    ResetPasswordCommand.of("garbage", "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(PasswordResetLimitedException.class);
    verifyNoInteractions(tokens, users, passwordHashing);
  }

  @Test
  @DisplayName("新密码不合规：422 newPassword，令牌不被消费")
  void should_not_consume_token_when_new_password_is_invalid() {
    assertThatThrownBy(
            () ->
                handler.handle(ResetPasswordCommand.of(SECRET.value(), "short", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("newPassword", "TOO_SHORT")));
    verify(tokens, never()).consume(any(), any(), any());
    verifyNoInteractions(passwordHashing);
  }

  @Test
  @DisplayName("令牌为空：422 token/REQUIRED；格式不对：422 token/INVALID，不查库")
  void should_reject_blank_or_malformed_token() {
    assertThatThrownBy(
            () ->
                handler.handle(ResetPasswordCommand.of("", "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    assertThatThrownBy(
            () ->
                handler.handle(
                    ResetPasswordCommand.of("abc", "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("token", "INVALID")));
    verifyNoInteractions(tokens);
  }

  @Test
  @DisplayName("令牌格式不对且新密码不合规：一次报全 token/INVALID + newPassword/TOO_SHORT，不查库、不哈希")
  void should_report_malformed_token_and_invalid_password_together() {
    assertThatThrownBy(
            () -> handler.handle(ResetPasswordCommand.of("abc", "short", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("token", "INVALID"),
                        org.assertj.core.groups.Tuple.tuple("newPassword", "TOO_SHORT")));
    verifyNoInteractions(tokens, passwordHashing);
  }

  @Test
  @DisplayName("令牌用途不符：422 token/INVALID")
  void should_reject_wrong_purpose_token() {
    when(tokens.findByTokenHash(SECRET.hash()))
        .thenReturn(Optional.of(token(EmailTokenPurpose.EMAIL_VERIFY, EMAIL)));

    assertThatThrownBy(
            () ->
                handler.handle(
                    ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    verify(tokens, never()).consume(any(), any(), any());
  }

  @Test
  @DisplayName("用户已封禁：422，不改密码")
  void should_reject_when_user_is_banned() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(EMAIL)
                    .username(Username.of("chen-yu"))
                    .status(UserStatus.BANNED)
                    .bannedAt(Instant.EPOCH)
                    .version(0L)
                    .build()));

    assertThatThrownBy(
            () ->
                handler.handle(
                    ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    verify(credentials, never()).save(any());
  }

  @Test
  @DisplayName("令牌的邮箱和用户当前邮箱不同（期间改邮箱生效）：422，不改密码")
  void should_reject_when_token_email_differs_from_current() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(Optional.of(user(UserStatus.ACTIVE, EmailAddress.of("new@example.com"))));

    assertThatThrownBy(
            () ->
                handler.handle(
                    ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    verify(credentials, never()).save(any());
  }

  /// 提交后清零登录失败抛错（Redis 不可用）：重设已经生效，仍返回成功并照常发「密码已重设」。
  @Test
  @DisplayName("提交后清零登录失败抛错不影响重设结果与通知")
  void should_ignore_clear_failure_after_commit() {
    doThrow(new IllegalStateException("redis down"))
        .when(loginThrottle)
        .clear(AccountType.USER, EMAIL);

    PasswordReset result =
        handler.handle(
            ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", "127.0.0.1", null));

    assertThat(result).isNotNull();
    verify(emailDelivery).send(any(PasswordResetDoneMail.class));
  }

  /// 清零登录失败与会话无关，放在事务提交之后。
  @Test
  @DisplayName("清零登录失败（与会话无关的 Redis 写入）在事务提交之后")
  void should_clear_login_failures_after_commit() {
    AtomicBoolean clearedInTransaction = new AtomicBoolean(true);
    doAnswer(
            invocation -> {
              clearedInTransaction.set(inTransaction.get());
              return null;
            })
        .when(loginThrottle)
        .clear(AccountType.USER, EMAIL);

    handler.handle(ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", "127.0.0.1", null));

    assertThat(clearedInTransaction).isFalse();
  }

  @Test
  @DisplayName("成功：换哈希 → 批量作废令牌 → 写验证时间 → 撤销全部会话并记 REVOKED → 登记「密码已重设」（事务内）→ 提交后清零登录失败")
  void should_reset_in_order_and_clear_login_failures() {
    PasswordReset result =
        handler.handle(
            ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", "127.0.0.1", null));

    InOrder order =
        inOrder(tokens, credentials, users, sessions, records, loginThrottle, emailDelivery);
    order.verify(tokens).consume(SECRET.hash(), EmailTokenPurpose.PASSWORD_RESET, NOW);
    order.verify(credentials).save(argThat(c -> c.getPasswordHash().equals(NEW_HASH)));
    order.verify(tokens).revokeAllActive(42L, NOW);
    order.verify(users).save(argThat(User::isEmailVerified));
    order.verify(sessions).revokeAll(AccountType.USER, 42L);
    order.verify(records).save(argThat(r -> r.getEndReason() == LoginEndReason.PASSWORD_RESET));
    order
        .verify(emailDelivery)
        .send(
            argThat(
                m ->
                    m instanceof PasswordResetDoneMail mail
                        && mail.to().equals(EMAIL)
                        && mail.notice().device().equals("Chrome 141 · macOS")
                        && mail.notice().ipAddress().equals("127.0.0.1")
                        && mail.notice().ipLocation().equals("内网")));
    order.verify(loginThrottle).clear(AccountType.USER, EMAIL);
    assertThat(result.email()).isEqualTo("chen.yu@example.com");
  }

  @Test
  @DisplayName("「密码已重设」在重设的事务里登记")
  void should_register_reset_done_mail_inside_transaction() {
    List<Boolean> sentInTransaction = new ArrayList<>();
    doAnswer(
            invocation -> {
              sentInTransaction.add(inTransaction.get());
              return null;
            })
        .when(emailDelivery)
        .send(any());

    handler.handle(ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", "127.0.0.1", null));

    assertThat(sentInTransaction).containsExactly(true);
  }

  @Test
  @DisplayName("没有凭据行（撤销改邮箱后）：新建一行")
  void should_create_credential_when_user_has_none() {
    when(credentials.findByUserId(42L)).thenReturn(Optional.empty());

    handler.handle(ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", "127.0.0.1", null));

    verify(credentials)
        .save(argThat(c -> c.getId() == null && c.getPasswordHash().equals(NEW_HASH)));
  }

  @Test
  @DisplayName("命令的 toString 不输出令牌与密码")
  void should_hide_token_and_password_in_command_to_string() {
    assertThat(ResetPasswordCommand.of(SECRET.value(), "Another-Horse-03", null, null).toString())
        .doesNotContain(SECRET.value())
        .doesNotContain("Another-Horse-03");
  }

  /// 用户 42 的一枚令牌。
  ///
  /// @param purpose 用途
  /// @param email 发往的邮箱
  /// @return 令牌
  private static EmailToken token(EmailTokenPurpose purpose, EmailAddress email) {
    return EmailToken.issue(purpose, 42L, email, SECRET, NOW.minusSeconds(60), "127.0.0.1");
  }

  /// 指定状态与邮箱的用户 42。
  ///
  /// @param status 状态
  /// @param email 邮箱
  /// @return 用户
  private static User user(UserStatus status, EmailAddress email) {
    User.UserBuilder builder =
        User.restore()
            .id(42L)
            .email(email)
            .username(Username.of("chen-yu"))
            .status(status)
            .version(0L);
    return status == UserStatus.PENDING_DELETION
        ? builder.deletionDueAt(Instant.EPOCH).build()
        : builder.build();
  }
}
