package dev.patra.identity.app.account.usecase.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.service.CurrentPasswordVerifier;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.domain.account.exception.CurrentPasswordMismatchException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.PasswordChangedMail;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.LoginAttempt;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.model.vo.SessionRotation;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// ChangePasswordHandler 单元测试：字段、状态、当前密码、新旧相同、事务里的顺序、事务后发信。
@DisplayName("ChangePasswordHandler 单元测试")
class ChangePasswordHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final PasswordHash OLD_HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final PasswordHash NEW_HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$bmV3aGFzaA");
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
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final ClientOriginResolver resolver =
      new ClientOriginResolver(
          userAgent -> DeviceProfile.of("Chrome 141 · macOS", DeviceClass.DESKTOP),
          ip -> Optional.of("中国 广东 深圳"));
  private final AtomicBoolean inTransaction = new AtomicBoolean(false);
  private final ChangePasswordHandler handler =
      new ChangePasswordHandler(
          currentUserPort,
          users,
          credentials,
          userReadPort,
          passwordHashing,
          new PasswordPolicy(Set.of("password123")::contains),
          new CurrentPasswordVerifier(credentials, passwordHashing, loginThrottle),
          tokens,
          sessions,
          new SessionTerminator(sessions, records),
          resolver,
          emailDelivery,
          recording(inTransaction),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findById(42L)).thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(credentials.findByUserId(42L))
        .thenAnswer(
            invocation -> Optional.of(UserPasswordCredential.restore(7L, 42L, OLD_HASH, 0L)));
    when(credentials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(loginThrottle.begin(AccountType.USER, EMAIL)).thenReturn(ATTEMPT);
    when(loginThrottle.recordFailure(ATTEMPT)).thenReturn(Optional.empty());
    when(passwordHashing.matches(any(), any())).thenReturn(true);
    when(passwordHashing.hash(any())).thenReturn(NEW_HASH);
    when(sessions.rotate(AccountType.USER, 42L, 7001L, Duration.ZERO))
        .thenReturn(Optional.of(SessionRotation.rotated(NEW_TOKEN)));
    when(sessions.revokeAllExcept(AccountType.USER, 42L, 7001L)).thenReturn(List.of(7002L, 7003L));
    when(records.findAllByIds(List.of(7002L, 7003L))).thenReturn(List.of(record(), record()));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
  }

  @Test
  @DisplayName("两个字段一起报：当前密码为空、新密码太短；不碰登录失败限制")
  void should_report_both_field_errors_at_once() {
    assertThatThrownBy(
            () -> handler.handle(ChangePasswordCommand.of("", "short", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("currentPassword", "REQUIRED"),
                        org.assertj.core.groups.Tuple.tuple("newPassword", "TOO_SHORT")));
    verifyNoInteractions(loginThrottle, sessions, emailDelivery);
  }

  @Test
  @DisplayName("注销冷静期内：409，不碰登录失败限制")
  void should_reject_pending_deletion_with_409() {
    when(users.findById(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(
            () ->
                handler.handle(
                    ChangePasswordCommand.of(
                        "Correct-Horse-02", "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(UserPendingDeletionException.class);
    verifyNoInteractions(loginThrottle, sessions, emailDelivery);
  }

  @Test
  @DisplayName("当前密码错：按失败结算，401 PASSWORD_MISMATCH，不哈希新密码、不发信")
  void should_reject_wrong_current_password_with_401_and_count_failure() {
    when(passwordHashing.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(
            () ->
                handler.handle(
                    ChangePasswordCommand.of("wrong", "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(CurrentPasswordMismatchException.class);
    verify(loginThrottle).recordFailure(ATTEMPT);
    verify(passwordHashing, never()).hash(any());
    verifyNoInteractions(sessions, emailDelivery);
  }

  @Test
  @DisplayName("并发改密码：锁住用户行后凭据已不是刚校验过的那一个，401 PASSWORD_MISMATCH 且不计失败、不换哈希、不换令牌、不发信")
  void should_reject_when_credential_changed_between_verification_and_lock() {
    PasswordHash concurrent = PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$b3RoZXI");
    when(credentials.findByUserId(42L))
        .thenReturn(Optional.of(UserPasswordCredential.restore(7L, 42L, OLD_HASH, 0L)))
        .thenReturn(Optional.of(UserPasswordCredential.restore(7L, 42L, concurrent, 1L)));

    assertThatThrownBy(
            () ->
                handler.handle(
                    ChangePasswordCommand.of(
                        "Correct-Horse-02", "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(CurrentPasswordMismatchException.class);
    verify(loginThrottle, never()).recordFailure(any());
    verify(credentials, never()).save(any());
    verify(sessions, never()).rotate(any(), anyLong(), anyLong(), any());
    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("新旧相同（含 NFKC 等价的全角形式）：422 newPassword/UNCHANGED，不做第二次哈希")
  void should_reject_unchanged_password_with_422_without_hashing() {
    assertThatThrownBy(
            () ->
                handler.handle(
                    ChangePasswordCommand.of(
                        "Correct-Horse-02", "Correct-Horse-02", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("newPassword", "UNCHANGED")));
    assertThatThrownBy(
            () ->
                handler.handle(
                    ChangePasswordCommand.of(
                        "Correct-Horse-02", "Ｃorrect-Horse-02", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    verify(passwordHashing, never()).hash(any());
    verifyNoInteractions(sessions, emailDelivery);
  }

  @Test
  @DisplayName("成功：换哈希 → 批量作废令牌 → 撤销其余并记 REVOKED → 读账号 → 本机换令牌（事务最后一步）→ 发「密码已修改」；返回新令牌")
  void should_change_in_order_rotate_keep_current_and_revoke_the_rest() {
    AuthenticatedUser result =
        handler.handle(
            ChangePasswordCommand.of("Correct-Horse-02", "Another-Horse-03", "127.0.0.1", null));

    InOrder order = inOrder(credentials, tokens, sessions, records, userReadPort, emailDelivery);
    order.verify(credentials).save(argThat(c -> c.getPasswordHash().equals(NEW_HASH)));
    order.verify(tokens).revokeAllActive(42L, NOW);
    order.verify(sessions).revokeAllExcept(AccountType.USER, 42L, 7001L);
    order
        .verify(records, times(2))
        .save(argThat(r -> r.getEndReason() == LoginEndReason.PASSWORD_CHANGED));
    order.verify(userReadPort).findAccount(42L);
    order.verify(sessions).rotate(AccountType.USER, 42L, 7001L, Duration.ZERO);
    order
        .verify(emailDelivery)
        .send(
            argThat(
                m ->
                    m instanceof PasswordChangedMail mail
                        && mail.to().equals(EMAIL)
                        && mail.displayName().equals("陈宇")
                        && mail.notice().occurredAt().equals(NOW)
                        && mail.notice().notMeAdvice().equals(PasswordChangedMail.NOT_ME_ADVICE)
                        && mail.notice().device().equals("Chrome 141 · macOS")
                        && mail.notice().ipAddress().equals("127.0.0.1")
                        && mail.notice().ipLocation().equals("内网")));
    assertThat(result.sessionToken()).isEqualTo(NEW_TOKEN);
    assertThat(result.account()).isEqualTo(ACCOUNT);
  }

  @Test
  @DisplayName("哈希在事务外，换哈希在事务内，发信在事务提交后")
  void should_send_mail_only_after_transaction() {
    when(passwordHashing.hash(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isFalse();
              return NEW_HASH;
            });
    when(credentials.save(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return invocation.getArgument(0);
            });
    doAnswer(
            invocation -> {
              assertThat(inTransaction).isFalse();
              return null;
            })
        .when(emailDelivery)
        .send(any());

    handler.handle(
        ChangePasswordCommand.of("Correct-Horse-02", "Another-Horse-03", "127.0.0.1", null));

    verify(emailDelivery).send(any());
  }

  @Test
  @DisplayName("换令牌时 Redis 失败：异常从事务里抛出（回滚，密码没改成），不发信")
  void should_roll_back_password_when_session_rotation_fails() {
    TemporarilyUnavailableException down = new TemporarilyUnavailableException();
    when(sessions.rotate(AccountType.USER, 42L, 7001L, Duration.ZERO)).thenThrow(down);

    assertThatThrownBy(
            () ->
                handler.handle(
                    ChangePasswordCommand.of(
                        "Correct-Horse-02", "Another-Horse-03", "127.0.0.1", null)))
        .isSameAs(down);
    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("换令牌是事务里最后一步：之前任何一步失败都不碰旧令牌，用户还能在原会话里重试")
  void should_not_rotate_when_a_step_before_rotation_fails() {
    when(userReadPort.findAccount(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                handler.handle(
                    ChangePasswordCommand.of(
                        "Correct-Horse-02", "Another-Horse-03", "127.0.0.1", null)))
        .isInstanceOf(dev.patra.tool.core.security.AuthenticationRequiredException.class);
    verify(sessions, never()).rotate(any(), anyLong(), anyLong(), any());
    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("命令的 toString 不输出两个密码")
  void should_hide_passwords_in_command_to_string() {
    assertThat(
            ChangePasswordCommand.of("Correct-Horse-02", "Another-Horse-03", null, null).toString())
        .doesNotContain("Correct-Horse-02")
        .doesNotContain("Another-Horse-03");
  }

  /// 指定状态的用户 42。
  ///
  /// @param status 状态
  /// @return 用户
  private static User user(UserStatus status) {
    User.UserBuilder builder =
        User.restore()
            .id(42L)
            .email(EMAIL)
            .username(Username.of("chen-yu"))
            .status(status)
            .version(0L);
    return status == UserStatus.PENDING_DELETION
        ? builder.deletionDueAt(Instant.EPOCH).build()
        : builder.build();
  }

  /// 一条未结束的登录记录。
  ///
  /// @return 记录
  private static UserLoginRecord record() {
    return UserLoginRecord.start(
        42L,
        LoginClient.of("web", null),
        ClientOrigin.builder().deviceClass(DeviceClass.UNKNOWN).build(),
        NOW.plusSeconds(3600));
  }

  /// 记录事务边界的事务操作：回调同步执行。
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
}
