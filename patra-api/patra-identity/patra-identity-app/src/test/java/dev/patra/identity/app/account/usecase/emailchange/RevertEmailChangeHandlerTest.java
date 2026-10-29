package dev.patra.identity.app.account.usecase.emailchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangeRevertedMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.security.AccountType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// RevertEmailChangeHandler 单元测试：注销中可撤、被抢注 409 且令牌不消费、撤销的顺序。
@DisplayName("RevertEmailChangeHandler 单元测试")
class RevertEmailChangeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final EmailAddress OLD = EmailAddress.of("chen.yu@example.com");
  private static final EmailAddress NEW = EmailAddress.of("new@example.com");
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("V".repeat(43));
  private static final EmailTokenSecret RESET_SECRET = new EmailTokenSecret("R".repeat(43));
  private static final MailLinkPolicy LINKS =
      MailLinkPolicy.builder()
          .baseUrl("https://patra.example")
          .verifyEmailPath("/verify-email")
          .securitySettingsPath("/settings/security")
          .resetPasswordPath("/reset-password")
          .confirmEmailChangePath("/confirm-email-change")
          .revertEmailChangePath("/revert-email-change")
          .build();
  private static final UserAccountReadModel ACCOUNT =
      UserAccountReadModel.builder()
          .userId(42L)
          .email("chen.yu@example.com")
          .emailVerified(true)
          .username("chen-yu")
          .displayName("陈宇")
          .status(UserStatus.ACTIVE)
          .build();

  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final EmailTokenIssuer emailTokenIssuer = mock(EmailTokenIssuer.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final ClientOriginResolver resolver =
      new ClientOriginResolver(
          userAgent -> DeviceProfile.of("Chrome 141 · macOS", DeviceClass.DESKTOP),
          ip -> Optional.of("中国 广东 深圳"));
  private final AtomicBoolean inTransaction = new AtomicBoolean(false);
  private final RevertEmailChangeHandler handler =
      new RevertEmailChangeHandler(
          tokens,
          users,
          userReadPort,
          credentials,
          new SessionTerminator(sessions, records),
          emailTokenIssuer,
          LINKS,
          resolver,
          emailDelivery,
          RequestEmailChangeHandlerTest.recording(inTransaction),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(tokens.findByTokenHash(SECRET.hash()))
        .thenReturn(Optional.of(token(EmailTokenPurpose.EMAIL_CHANGE_REVERT, OLD)));
    when(tokens.consume(SECRET.hash(), EmailTokenPurpose.EMAIL_CHANGE_REVERT, NOW))
        .thenReturn(Optional.of(token(EmailTokenPurpose.EMAIL_CHANGE_REVERT, OLD)));
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(users.existsByEmail(OLD)).thenReturn(false);
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of(7001L));
    when(records.findAllByIds(List.of(7001L)))
        .thenReturn(
            List.of(
                UserLoginRecord.start(
                    42L,
                    LoginClient.of("web", null),
                    ClientOrigin.builder().deviceClass(DeviceClass.UNKNOWN).build(),
                    NOW.plusSeconds(60))));
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any()))
        .thenReturn(
            IssuedEmailToken.of(
                EmailToken.issue(
                    EmailTokenPurpose.PASSWORD_RESET, 42L, OLD, RESET_SECRET, NOW, null),
                RESET_SECRET));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
  }

  @Test
  @DisplayName("注销冷静期内也能撤销；状态不动")
  void should_allow_pending_deletion_user() {
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));

    handler.handle(RevertEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null));

    verify(users)
        .save(argThat(u -> u.isPendingDeletion() && u.getEmail().orElseThrow().equals(OLD)));
  }

  @Test
  @DisplayName("已封禁：422，不消费")
  void should_reject_banned_user() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(NEW)
                    .username(Username.of("chen-yu"))
                    .status(UserStatus.BANNED)
                    .bannedAt(Instant.EPOCH)
                    .version(0L)
                    .build()));

    assertThatThrownBy(
            () -> handler.handle(RevertEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    verify(tokens, never()).consume(any(), any(), any());
  }

  @Test
  @DisplayName("旧地址 7 天内被别人注册：409，事务回滚（令牌不算消费）")
  void should_reject_with_409_and_keep_token_when_old_email_taken() {
    when(users.existsByEmail(OLD)).thenReturn(true);

    assertThatThrownBy(
            () -> handler.handle(RevertEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null)))
        .isInstanceOf(UserIdentifierTakenException.class);
    verify(users, never()).save(any());
    verifyNoInteractions(credentials, sessions, emailDelivery);
  }

  @Test
  @DisplayName("X → Y → X 之后点 R_X：当前邮箱已经是要改回的地址，不算被别人占用，照常撤销")
  void should_not_treat_own_current_email_as_taken() {
    when(users.findByIdForUpdate(42L))
        .thenAnswer(
            invocation ->
                Optional.of(
                    User.restore()
                        .id(42L)
                        .email(OLD)
                        .emailVerifiedAt(NOW.minusSeconds(3600))
                        .username(Username.of("chen-yu"))
                        .status(UserStatus.ACTIVE)
                        .version(0L)
                        .build()));
    when(users.existsByEmail(OLD)).thenReturn(true);

    EmailChangeReverted result =
        handler.handle(RevertEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null));

    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    verify(users).save(argThat(u -> u.getEmail().orElseThrow().equals(OLD)));
    verify(credentials).deleteByUserId(42L);
    verify(emailDelivery).send(any());
  }

  @Test
  @DisplayName("成功：改回 → 作废全部 + 只作废比本条更晚签发的撤销令牌（更早的保留）→ 删凭据 → 撤销全部会话并记 REVOKED → 签发重设 → 发到改回的地址")
  void should_revert_in_order() {
    EmailChangeReverted result =
        handler.handle(RevertEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null));

    InOrder order =
        inOrder(users, tokens, credentials, sessions, records, emailTokenIssuer, emailDelivery);
    order.verify(tokens).consume(SECRET.hash(), EmailTokenPurpose.EMAIL_CHANGE_REVERT, NOW);
    order
        .verify(users)
        .save(argThat(u -> u.getEmail().orElseThrow().equals(OLD) && u.isEmailVerified()));
    order.verify(tokens).revokeAllActive(42L, NOW);
    order.verify(tokens).revokeRevertTokensIssuedAfter(42L, 500L, NOW);
    order.verify(credentials).deleteByUserId(42L);
    order.verify(sessions).revokeAll(AccountType.USER, 42L);
    order.verify(records).save(argThat(r -> r.getEndReason() == LoginEndReason.EMAIL_REVERTED));
    order.verify(emailTokenIssuer).issue(EmailTokenPurpose.PASSWORD_RESET, 42L, OLD, "127.0.0.1");
    order
        .verify(emailDelivery)
        .send(
            argThat(
                m ->
                    m instanceof EmailChangeRevertedMail r
                        && r.to().equals(OLD)
                        && r.resetUrl()
                            .equals(
                                "https://patra.example/reset-password#token="
                                    + RESET_SECRET.value())
                        && r.notice().notMeAdvice().equals(EmailChangeRevertedMail.NOT_ME_ADVICE)
                        && r.notice().device().equals("Chrome 141 · macOS")
                        && r.notice().ipAddress().equals("127.0.0.1")
                        && r.notice().ipLocation().equals("内网")));
    assertThat(result.email()).isEqualTo("chen.yu@example.com");
  }

  @Test
  @DisplayName("命令的 toString 不输出令牌")
  void should_hide_token_in_command_to_string() {
    assertThat(RevertEmailChangeCommand.of(SECRET.value(), null, null).toString())
        .doesNotContain(SECRET.value());
    assertThat(ConfirmEmailChangeCommand.of(SECRET.value(), null, null).toString())
        .doesNotContain(SECRET.value());
  }

  /// 用户 42 的一枚令牌。
  ///
  /// @param purpose 用途
  /// @param email 发往的邮箱
  /// @return 令牌
  private static EmailToken token(EmailTokenPurpose purpose, EmailAddress email) {
    return EmailToken.restore()
        .id(500L)
        .userId(42L)
        .purpose(purpose)
        .email(email)
        .tokenHash(SECRET.hash())
        .expiresAt(NOW.plus(java.time.Duration.ofDays(7)))
        .version(0L)
        .build();
  }

  /// 指定状态的用户 42，邮箱已改成 NEW。
  ///
  /// @param status 状态
  /// @return 用户
  private static User user(UserStatus status) {
    User.UserBuilder builder =
        User.restore()
            .id(42L)
            .email(NEW)
            .emailVerifiedAt(NOW.minusSeconds(3600))
            .username(Username.of("chen-yu"))
            .status(status)
            .version(0L);
    return status == UserStatus.PENDING_DELETION
        ? builder.deletionDueAt(Instant.EPOCH).build()
        : builder.build();
  }
}
