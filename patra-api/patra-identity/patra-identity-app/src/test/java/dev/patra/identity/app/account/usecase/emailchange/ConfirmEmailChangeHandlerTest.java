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
import dev.patra.identity.domain.account.model.vo.mail.EmailChangedConfirmationMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangedNoticeMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.tool.core.error.field.FieldViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// ConfirmEmailChangeHandler 单元测试：令牌检查、状态、抢注、生效的顺序与两封邮件。
@DisplayName("ConfirmEmailChangeHandler 单元测试")
class ConfirmEmailChangeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final EmailAddress OLD = EmailAddress.of("chen.yu@example.com");
  private static final EmailAddress NEW = EmailAddress.of("new@example.com");
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("C".repeat(43));
  private static final EmailTokenSecret REVERT_SECRET = new EmailTokenSecret("V".repeat(43));
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
          .email("new@example.com")
          .emailVerified(true)
          .username("chen-yu")
          .displayName("陈宇")
          .status(UserStatus.ACTIVE)
          .build();

  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final EmailTokenIssuer emailTokenIssuer = mock(EmailTokenIssuer.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final ClientOriginResolver resolver =
      new ClientOriginResolver(
          userAgent -> DeviceProfile.of("Chrome 141 · macOS", DeviceClass.DESKTOP),
          ip -> Optional.of("中国 广东 深圳"));
  private final AtomicBoolean inTransaction = new AtomicBoolean(false);
  private final ConfirmEmailChangeHandler handler =
      new ConfirmEmailChangeHandler(
          tokens,
          users,
          userReadPort,
          emailTokenIssuer,
          LINKS,
          resolver,
          emailDelivery,
          RequestEmailChangeHandlerTest.recording(inTransaction),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(tokens.findByTokenHash(SECRET.hash()))
        .thenReturn(Optional.of(token(EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NEW)));
    when(tokens.consume(SECRET.hash(), EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW))
        .thenReturn(Optional.of(token(EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NEW)));
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(users.existsByEmail(NEW)).thenReturn(false);
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any()))
        .thenReturn(
            IssuedEmailToken.of(
                EmailToken.issue(
                    EmailTokenPurpose.EMAIL_CHANGE_REVERT, 42L, OLD, REVERT_SECRET, NOW, null),
                REVERT_SECRET));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
  }

  @Test
  @DisplayName("令牌为空或格式不对：422，不查库")
  void should_reject_blank_or_malformed_token() {
    assertThatThrownBy(() -> handler.handle(ConfirmEmailChangeCommand.of("", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    assertThatThrownBy(() -> handler.handle(ConfirmEmailChangeCommand.of("abc", "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    verifyNoInteractions(tokens);
  }

  @Test
  @DisplayName("用途不符：422，不消费")
  void should_reject_wrong_purpose_token() {
    when(tokens.findByTokenHash(SECRET.hash()))
        .thenReturn(Optional.of(token(EmailTokenPurpose.PASSWORD_RESET, NEW)));

    assertThatThrownBy(
            () -> handler.handle(ConfirmEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class);
    verify(tokens, never()).consume(any(), any(), any());
  }

  @Test
  @DisplayName("用户不是 ACTIVE（注销中）：422，不消费")
  void should_reject_non_active_user_with_422() {
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(
            () -> handler.handle(ConfirmEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("token", "INVALID")));
    verify(tokens, never()).consume(any(), any(), any());
  }

  @Test
  @DisplayName("一小时内新邮箱被别人注册：409 email/ALREADY_REGISTERED，不改邮箱")
  void should_reject_with_409_when_new_email_taken_meanwhile() {
    when(users.existsByEmail(NEW)).thenReturn(true);

    assertThatThrownBy(
            () -> handler.handle(ConfirmEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null)))
        .isInstanceOf(UserIdentifierTakenException.class)
        .satisfies(
            e ->
                assertThat(((UserIdentifierTakenException) e).getFieldViolations())
                    .extracting(FieldViolation::field)
                    .containsExactly("email"));
    verify(users, never()).save(any());
    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("成功：消费 → 改邮箱并写验证时间 → 批量作废令牌 → 签发撤销令牌 → 先旧邮箱通知（含撤销链接与遮掩的新地址）后新邮箱确认")
  void should_change_email_revoke_tokens_issue_revert_and_send_two_mails_old_first() {
    EmailChanged result =
        handler.handle(ConfirmEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null));

    InOrder order = inOrder(tokens, users, emailTokenIssuer, emailDelivery);
    order.verify(tokens).consume(SECRET.hash(), EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW);
    order
        .verify(users)
        .save(argThat(u -> u.getEmail().orElseThrow().equals(NEW) && u.isEmailVerified()));
    order.verify(tokens).revokeAllActive(42L, NOW);
    order
        .verify(emailTokenIssuer)
        .issue(EmailTokenPurpose.EMAIL_CHANGE_REVERT, 42L, OLD, "127.0.0.1");
    order
        .verify(emailDelivery)
        .send(
            argThat(
                m ->
                    m instanceof EmailChangedNoticeMail n
                        && n.to().equals(OLD)
                        && n.newEmailMasked().equals("n***@example.com")
                        && n.revertUrl()
                            .equals(
                                "https://patra.example/revert-email-change#token="
                                    + REVERT_SECRET.value())
                        && n.linkExpiresAt()
                            .equals(NOW.plus(EmailTokenPurpose.EMAIL_CHANGE_REVERT.validity()))
                        && n.notice().device().equals("Chrome 141 · macOS")
                        && n.notice().ipAddress().equals("127.0.0.1")
                        && n.notice().ipLocation().equals("内网")));
    order
        .verify(emailDelivery)
        .send(argThat(m -> m instanceof EmailChangedConfirmationMail c && c.to().equals(NEW)));
    assertThat(result.email()).isEqualTo("new@example.com");
    assertThat(result.emailVerifiedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("签发与两封邮件的登记都在事务内")
  void should_register_both_mails_inside_transaction() {
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return IssuedEmailToken.of(
                  EmailToken.issue(
                      EmailTokenPurpose.EMAIL_CHANGE_REVERT, 42L, OLD, REVERT_SECRET, NOW, null),
                  REVERT_SECRET);
            });
    org.mockito.Mockito.doAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return null;
            })
        .when(emailDelivery)
        .send(any());

    handler.handle(ConfirmEmailChangeCommand.of(SECRET.value(), "127.0.0.1", null));

    verify(emailDelivery, org.mockito.Mockito.times(2)).send(any());
  }

  /// 用户 42 的一枚令牌。
  ///
  /// @param purpose 用途
  /// @param email 发往的邮箱
  /// @return 令牌
  private static EmailToken token(EmailTokenPurpose purpose, EmailAddress email) {
    return EmailToken.issue(purpose, 42L, email, SECRET, NOW.minusSeconds(60), "127.0.0.1");
  }

  /// 指定状态的用户 42，邮箱 OLD。
  ///
  /// @param status 状态
  /// @return 用户
  private static User user(UserStatus status) {
    User.UserBuilder builder =
        User.restore()
            .id(42L)
            .email(OLD)
            .emailVerifiedAt(NOW.minusSeconds(3600))
            .username(Username.of("chen-yu"))
            .status(status)
            .version(0L);
    return status == UserStatus.PENDING_DELETION
        ? builder.deletionDueAt(Instant.EPOCH).build()
        : builder.build();
  }
}
