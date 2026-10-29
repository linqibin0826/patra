package dev.patra.identity.app.account.usecase.emailchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.EmailChangeLimitedException;
import dev.patra.identity.domain.account.exception.EmailChangePendingException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangeConfirmMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.EmailChangeThrottlePort;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AccountType;
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

/// RequestEmailChangeHandler 单元测试：字段 → 状态 → 再认证 → 待确认 → 占用 → 限流 → 签发 → 发信。
@DisplayName("RequestEmailChangeHandler 单元测试")
class RequestEmailChangeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final EmailAddress OLD = EmailAddress.of("chen.yu@example.com");
  private static final EmailAddress NEW = EmailAddress.of("new@example.com");
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("C".repeat(43));
  private static final IssuedEmailToken ISSUED =
      IssuedEmailToken.of(
          EmailToken.issue(EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, 42L, NEW, SECRET, NOW, null),
          SECRET);
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

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final ReauthenticationGuard reauthenticationGuard = mock(ReauthenticationGuard.class);
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final EmailChangeThrottlePort emailChangeThrottle = mock(EmailChangeThrottlePort.class);
  private final EmailTokenIssuer emailTokenIssuer = mock(EmailTokenIssuer.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final AtomicBoolean inTransaction = new AtomicBoolean(false);
  private final RequestEmailChangeHandler handler =
      new RequestEmailChangeHandler(
          currentUserPort,
          users,
          userReadPort,
          reauthenticationGuard,
          tokens,
          emailChangeThrottle,
          emailTokenIssuer,
          LINKS,
          emailDelivery,
          recording(inTransaction),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findById(42L)).thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(tokens.findActive(anyLong(), any(), any())).thenReturn(Optional.empty());
    when(users.existsByEmail(NEW)).thenReturn(false);
    when(emailChangeThrottle.acquire(anyLong(), any())).thenReturn(Optional.empty());
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any())).thenReturn(ISSUED);
  }

  @Test
  @DisplayName("新邮箱格式不对或与当前相同：422，不碰守卫")
  void should_reject_same_as_current_email_with_422() {
    assertThatThrownBy(
            () ->
                handler.handle(RequestEmailChangeCommand.of(" Chen.Yu@Example.com ", "127.0.0.1")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("newEmail", "UNCHANGED")));
    assertThatThrownBy(() -> handler.handle(RequestEmailChangeCommand.of("nope", "127.0.0.1")))
        .isInstanceOf(InvalidUserFieldsException.class);
    verifyNoInteractions(reauthenticationGuard, tokens, emailChangeThrottle, emailDelivery);
  }

  @Test
  @DisplayName("注销冷静期内：409，不碰守卫")
  void should_reject_pending_deletion_before_reauth() {
    when(users.findById(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(
            () -> handler.handle(RequestEmailChangeCommand.of("new@example.com", "127.0.0.1")))
        .isInstanceOf(UserPendingDeletionException.class);
    verifyNoInteractions(reauthenticationGuard, tokens, emailChangeThrottle);
  }

  @Test
  @DisplayName("顺序：状态 → 再认证 → 待确认 → 占用 → 限流 → 事务签发 → 事务后发到新邮箱")
  void should_check_in_order_fields_status_reauth_pending_taken_throttle() {
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

    EmailChangeRequested result =
        handler.handle(RequestEmailChangeCommand.of("New@Example.com", "127.0.0.1"));

    InOrder order =
        inOrder(
            users,
            reauthenticationGuard,
            tokens,
            emailChangeThrottle,
            emailTokenIssuer,
            emailDelivery);
    order.verify(users).findById(42L);
    order.verify(reauthenticationGuard).requireRecent(CURRENT);
    order.verify(tokens).findActive(42L, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW);
    order.verify(users).existsByEmail(NEW);
    order.verify(emailChangeThrottle).acquire(42L, NOW);
    order.verify(users).findByIdForUpdate(42L);
    order
        .verify(emailTokenIssuer)
        .issue(EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, 42L, NEW, "127.0.0.1");
    order
        .verify(emailDelivery)
        .send(
            EmailChangeConfirmMail.of(
                NEW, "陈宇", "https://patra.example/confirm-email-change#token=" + SECRET.value()));
    assertThat(result.newEmail()).isEqualTo("new@example.com");
    assertThat(result.expiresAt()).isEqualTo(ISSUED.token().getExpiresAt());
  }

  @Test
  @DisplayName("超过再认证窗口：403 原样抛出，不查待确认、不限流")
  void should_require_reauth_before_pending_check() {
    ReauthenticationRequiredException required = new ReauthenticationRequiredException();
    org.mockito.Mockito.doThrow(required).when(reauthenticationGuard).requireRecent(CURRENT);

    assertThatThrownBy(
            () -> handler.handle(RequestEmailChangeCommand.of("new@example.com", "127.0.0.1")))
        .isSameAs(required);
    verifyNoInteractions(tokens, emailChangeThrottle, emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("已有待确认的改邮箱：409 EMAIL_CHANGE_PENDING，不限流")
  void should_reject_when_change_is_pending() {
    when(tokens.findActive(42L, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW))
        .thenReturn(Optional.of(ISSUED.token()));

    assertThatThrownBy(
            () -> handler.handle(RequestEmailChangeCommand.of("new@example.com", "127.0.0.1")))
        .isInstanceOf(EmailChangePendingException.class);
    verifyNoInteractions(emailChangeThrottle, emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("新邮箱已被别人使用：409 email/ALREADY_REGISTERED，不限流、不签发、不发信")
  void should_reject_taken_email_with_409_before_throttling() {
    when(users.existsByEmail(NEW)).thenReturn(true);

    assertThatThrownBy(
            () -> handler.handle(RequestEmailChangeCommand.of("new@example.com", "127.0.0.1")))
        .isInstanceOf(UserIdentifierTakenException.class)
        .satisfies(
            e ->
                assertThat(((UserIdentifierTakenException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("email", "ALREADY_REGISTERED")));
    verifyNoInteractions(emailChangeThrottle, emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("今天的配额用完：429 带等待时长，不签发")
  void should_throw_429_when_daily_quota_used() {
    when(emailChangeThrottle.acquire(42L, NOW)).thenReturn(Optional.of(Duration.ofHours(6)));

    assertThatThrownBy(
            () -> handler.handle(RequestEmailChangeCommand.of("new@example.com", "127.0.0.1")))
        .isInstanceOf(EmailChangeLimitedException.class);
    verifyNoInteractions(emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("锁后重判待确认：两次并发申请串行，第二次在锁后 409")
  void should_recheck_pending_after_locking() {
    when(tokens.findActive(42L, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(ISSUED.token()));

    assertThatThrownBy(
            () -> handler.handle(RequestEmailChangeCommand.of("new@example.com", "127.0.0.1")))
        .isInstanceOf(EmailChangePendingException.class);
    verify(emailTokenIssuer, org.mockito.Mockito.never()).issue(any(), anyLong(), any(), any());
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
            .username(Username.of("chen-yu"))
            .status(status)
            .version(0L);
    return status == UserStatus.PENDING_DELETION
        ? builder.deletionDueAt(Instant.EPOCH).build()
        : builder.build();
  }

  /// 记录事务边界的事务操作：回调同步执行。
  ///
  /// @param inTransaction 标记
  /// @return 事务操作
  static TransactionOperations recording(AtomicBoolean inTransaction) {
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
