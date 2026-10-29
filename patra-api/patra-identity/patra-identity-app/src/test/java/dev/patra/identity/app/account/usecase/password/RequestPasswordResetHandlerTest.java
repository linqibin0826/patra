package dev.patra.identity.app.account.usecase.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.PasswordResetLimitedException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.policy.PasswordResetPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.account.port.throttle.PasswordResetThrottlePort;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
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

/// RequestPasswordResetHandler 单元测试：格式、限流先于查用户、注册与否结果一致、不碰登录失败限制。
@DisplayName("RequestPasswordResetHandler 单元测试")
class RequestPasswordResetHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("R".repeat(43));
  private static final IssuedEmailToken ISSUED =
      IssuedEmailToken.of(
          EmailToken.issue(EmailTokenPurpose.PASSWORD_RESET, 42L, EMAIL, SECRET, NOW, "127.0.0.1"),
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
          .emailVerified(false)
          .username("chen-yu")
          .displayName("陈宇")
          .status(UserStatus.ACTIVE)
          .build();

  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final PasswordResetThrottlePort passwordResetThrottle =
      mock(PasswordResetThrottlePort.class);
  private final EmailTokenIssuer emailTokenIssuer = mock(EmailTokenIssuer.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final LoginThrottlePort loginThrottle = mock(LoginThrottlePort.class);
  private final AtomicBoolean inTransaction = new AtomicBoolean(false);
  private final RequestPasswordResetHandler handler =
      new RequestPasswordResetHandler(
          users,
          userReadPort,
          passwordResetThrottle,
          PasswordResetPolicy.of(Duration.ofSeconds(60), 5, 20, 10),
          emailTokenIssuer,
          LINKS,
          emailDelivery,
          recording(inTransaction),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(passwordResetThrottle.acquireRequest(any(), any(), any())).thenReturn(Optional.empty());
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
    when(emailTokenIssuer.issue(any(), anyLong(), any(), any())).thenReturn(ISSUED);
  }

  @Test
  @DisplayName("邮箱格式不对：422，不限流、不查用户")
  void should_reject_invalid_email_format_before_throttling() {
    assertThatThrownBy(() -> handler.handle(RequestPasswordResetCommand.of("nope", "127.0.0.1")))
        .isInstanceOf(InvalidUserFieldsException.class);
    verifyNoInteractions(passwordResetThrottle, users);
  }

  @Test
  @DisplayName("限流在查用户之前计数：未注册的邮箱也计")
  void should_count_throttle_before_looking_up_user() {
    handler.handle(RequestPasswordResetCommand.of(" Chen.Yu@Example.com ", "127.0.0.1"));

    InOrder order = inOrder(passwordResetThrottle, users);
    order.verify(passwordResetThrottle).acquireRequest(EMAIL, "127.0.0.1", NOW);
    order.verify(users).findByEmail(EMAIL);
  }

  @Test
  @DisplayName("被限流：429 带等待时长，不查用户")
  void should_throw_429_when_throttled() {
    when(passwordResetThrottle.acquireRequest(any(), any(), any()))
        .thenReturn(Optional.of(Duration.ofSeconds(50)));

    assertThatThrownBy(
            () ->
                handler.handle(RequestPasswordResetCommand.of("chen.yu@example.com", "127.0.0.1")))
        .isInstanceOf(PasswordResetLimitedException.class)
        .satisfies(
            e ->
                assertThat(((PasswordResetLimitedException) e).getRetryAfter())
                    .isEqualTo(Duration.ofSeconds(50)));
    verifyNoInteractions(users, emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("正常用户：事务里锁用户行并签发 PASSWORD_RESET，事务后发带重设链接的邮件；返回下次可申请时间")
  void should_issue_and_send_for_active_user() {
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

    PasswordResetRequested result =
        handler.handle(RequestPasswordResetCommand.of("chen.yu@example.com", "127.0.0.1"));

    InOrder order = inOrder(users, emailTokenIssuer, emailDelivery);
    order.verify(users).findByIdForUpdate(42L);
    order.verify(emailTokenIssuer).issue(EmailTokenPurpose.PASSWORD_RESET, 42L, EMAIL, "127.0.0.1");
    order
        .verify(emailDelivery)
        .send(
            PasswordResetMail.of(
                EMAIL, "陈宇", "https://patra.example/reset-password#token=" + SECRET.value()));
    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    assertThat(result.nextRequestAt()).isEqualTo(NOW.plusSeconds(60));
    verifyNoInteractions(loginThrottle);
  }

  @Test
  @DisplayName("未注册的邮箱：同样的 202 结果，不签发、不发信")
  void should_return_same_result_without_issuing_for_unknown_email() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

    PasswordResetRequested result =
        handler.handle(RequestPasswordResetCommand.of("chen.yu@example.com", "127.0.0.1"));

    assertThat(result)
        .isEqualTo(PasswordResetRequested.of("chen.yu@example.com", NOW.plusSeconds(60)));
    verifyNoInteractions(emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("查到用户后、锁行前用户被删或被封禁：和未注册一样静默 202，不签发、不发信")
  void should_stay_silent_when_user_vanishes_or_gets_banned_before_lock() {
    PasswordResetRequested expected =
        PasswordResetRequested.of("chen.yu@example.com", NOW.plusSeconds(60));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.empty());
    assertThat(handler.handle(RequestPasswordResetCommand.of("chen.yu@example.com", "127.0.0.1")))
        .isEqualTo(expected);

    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.BANNED)));
    assertThat(handler.handle(RequestPasswordResetCommand.of("chen.yu@example.com", "127.0.0.1")))
        .isEqualTo(expected);
    verifyNoInteractions(emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("被封禁的账号：不发信，结果同上")
  void should_not_send_for_banned_user() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.BANNED)));

    handler.handle(RequestPasswordResetCommand.of("chen.yu@example.com", "127.0.0.1"));

    verifyNoInteractions(emailTokenIssuer, emailDelivery);
  }

  @Test
  @DisplayName("注销冷静期内的账号：照发")
  void should_send_for_pending_deletion_user() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));

    handler.handle(RequestPasswordResetCommand.of("chen.yu@example.com", "127.0.0.1"));

    verify(emailDelivery).send(any(PasswordResetMail.class));
    verify(emailTokenIssuer).issue(eq(EmailTokenPurpose.PASSWORD_RESET), eq(42L), eq(EMAIL), any());
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
    return switch (status) {
      case BANNED -> builder.bannedAt(Instant.EPOCH).build();
      case PENDING_DELETION -> builder.deletionDueAt(Instant.EPOCH).build();
      default -> builder.build();
    };
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
