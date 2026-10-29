package dev.patra.identity.app.account.usecase.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.EmailAlreadyVerifiedException;
import dev.patra.identity.domain.account.exception.EmailResendLimitedException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.EmailVerificationMail;
import dev.patra.identity.domain.account.policy.EmailResendPolicy;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.EmailResendThrottlePort;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// ResendEmailVerificationHandler 单元测试：顺序、状态预判、限流、锁后重判、事务边界。
@DisplayName("ResendEmailVerificationHandler 单元测试")
class ResendEmailVerificationHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final EmailResendPolicy POLICY =
      EmailResendPolicy.of(Duration.ofSeconds(60), 5, 10, 30);
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("F".repeat(43));
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
          EMAIL, "陈宇", LINKS.verifyEmailUrl(SECRET), NOW.plus(Duration.ofHours(24)));
  private static final UserAccountReadModel ACCOUNT =
      UserAccountReadModel.builder()
          .userId(42L)
          .email("chen.yu@example.com")
          .emailVerified(false)
          .username("chen-yu")
          .displayName("陈宇")
          .status(UserStatus.ACTIVE)
          .build();

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final EmailResendThrottlePort emailResendThrottle = mock(EmailResendThrottlePort.class);
  private final EmailTokenIssuer emailTokenIssuer = mock(EmailTokenIssuer.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final AtomicBoolean inTransaction = new AtomicBoolean(false);
  private final ResendEmailVerificationHandler handler =
      new ResendEmailVerificationHandler(
          currentUserPort,
          users,
          userReadPort,
          emailResendThrottle,
          POLICY,
          emailTokenIssuer,
          LINKS,
          emailDelivery,
          recording(inTransaction),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 默认：当前用户 42，未验证的正常用户，限流放行，签发返回固定邮件。
  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findById(42L)).thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE, null)));
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE, null)));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
    when(emailResendThrottle.acquire(anyLong(), any(), any())).thenReturn(Optional.empty());
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any())).thenReturn(ISSUED);
  }

  @Test
  @DisplayName("顺序：预判状态 → 限流 → 加锁重读 → 签发（事务内）→ 发信（事务外）；返回邮箱和下次可重发时间")
  void should_resend_in_order_and_return_next_resend_at() {
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
        .when(emailDelivery)
        .send(any());

    EmailVerificationResent result =
        handler.handle(ResendEmailVerificationCommand.of("203.0.113.9"));

    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    assertThat(result.nextResendAt()).isEqualTo(NOW.plusSeconds(60));
    InOrder order = inOrder(users, emailResendThrottle, emailTokenIssuer, emailDelivery);
    order.verify(users).findById(42L);
    order.verify(emailResendThrottle).acquire(42L, "203.0.113.9", NOW);
    order.verify(users).findByIdForUpdate(42L);
    order
        .verify(emailTokenIssuer)
        .issue(eq(EmailTokenPurpose.EMAIL_VERIFY), eq(42L), eq(EMAIL), eq("203.0.113.9"));
    order.verify(emailDelivery).send(EXPECTED_MAIL);
  }

  @Test
  @DisplayName("已验证：409 EMAIL_ALREADY_VERIFIED，不碰限流、不签发、不发信")
  void should_return_409_when_already_verified_without_touching_throttle() {
    when(users.findById(42L))
        .thenReturn(Optional.of(user(UserStatus.ACTIVE, NOW.minus(Duration.ofDays(1)))));

    assertThatThrownBy(() -> handler.handle(ResendEmailVerificationCommand.of(null)))
        .isInstanceOf(EmailAlreadyVerifiedException.class);
    verifyNoInteractions(emailResendThrottle, emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("注销中：409 PENDING_DELETION，不碰限流")
  void should_return_409_when_pending_deletion() {
    when(users.findById(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION, null)));

    assertThatThrownBy(() -> handler.handle(ResendEmailVerificationCommand.of(null)))
        .isInstanceOf(UserPendingDeletionException.class);
    verifyNoInteractions(emailResendThrottle, emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("已封禁或用户不存在：401")
  void should_return_401_when_banned_or_missing() {
    when(users.findById(42L)).thenReturn(Optional.of(user(UserStatus.BANNED, null)));
    assertThatThrownBy(() -> handler.handle(ResendEmailVerificationCommand.of(null)))
        .isInstanceOf(AuthenticationRequiredException.class);

    when(users.findById(42L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.handle(ResendEmailVerificationCommand.of(null)))
        .isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(emailResendThrottle, emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("被限流：429 带等待时长，不进事务、不签发、不发信")
  void should_return_429_with_retry_after_and_not_issue() {
    when(emailResendThrottle.acquire(42L, "203.0.113.9", NOW))
        .thenReturn(Optional.of(Duration.ofSeconds(37)));

    assertThatThrownBy(() -> handler.handle(ResendEmailVerificationCommand.of("203.0.113.9")))
        .isInstanceOfSatisfying(
            EmailResendLimitedException.class,
            e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(37)));
    verify(users, never()).findByIdForUpdate(anyLong());
    verifyNoInteractions(emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("加锁重读发现这期间已经验证：409，不签发、不发信（限流已计数不退还）")
  void should_recheck_after_lock_and_not_send_when_verified_meanwhile() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(Optional.of(user(UserStatus.ACTIVE, NOW.minusSeconds(1))));

    assertThatThrownBy(() -> handler.handle(ResendEmailVerificationCommand.of(null)))
        .isInstanceOf(EmailAlreadyVerifiedException.class);
    verify(emailResendThrottle).acquire(42L, null, NOW);
    verifyNoInteractions(emailTokenIssuer, emailDelivery);
  }

  /// 一个用户。
  ///
  /// @param status 状态
  /// @param emailVerifiedAt 邮箱验证时间，可以为 `null`
  /// @return 用户
  private static User user(UserStatus status, Instant emailVerifiedAt) {
    return User.restore()
        .id(42L)
        .email(EMAIL)
        .emailVerifiedAt(emailVerifiedAt)
        .username(Username.of("chen-yu"))
        .status(status)
        .bannedAt(status == UserStatus.BANNED ? NOW : null)
        .deletionDueAt(status == UserStatus.PENDING_DELETION ? NOW.plus(Duration.ofDays(15)) : null)
        .version(0L)
        .build();
  }

  /// 执行回调期间把标记置真的事务操作。
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
