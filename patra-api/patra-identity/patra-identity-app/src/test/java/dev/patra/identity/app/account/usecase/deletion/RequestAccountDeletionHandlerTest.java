package dev.patra.identity.app.account.usecase.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionRequestedMail;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.policy.AccountDeletionPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

/// RequestAccountDeletionHandler 单元测试：字段 → 状态 → 用户名比对 → 再认证 → 事务（迁移、作废令牌、撤销会话）→ 发信。
@DisplayName("RequestAccountDeletionHandler 单元测试")
class RequestAccountDeletionHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
  private static final Duration COOLING = Duration.ofDays(15);
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
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
  private final SessionTerminator sessionTerminator = mock(SessionTerminator.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final ClientOriginResolver resolver =
      new ClientOriginResolver(
          userAgent -> DeviceProfile.of("Chrome 141 · macOS", DeviceClass.DESKTOP),
          ip -> Optional.of("中国 广东 深圳"));
  private final AtomicBoolean inTransaction = new AtomicBoolean(false);
  private final List<Boolean> sentInTransaction = new ArrayList<>();
  private final RequestAccountDeletionHandler handler =
      new RequestAccountDeletionHandler(
          currentUserPort,
          users,
          userReadPort,
          reauthenticationGuard,
          tokens,
          sessionTerminator,
          resolver,
          emailDelivery,
          AccountDeletionPolicy.of(COOLING, Duration.ofDays(3)),
          recording(inTransaction),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findById(42L)).thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
    when(sessionTerminator.terminateAll(anyLong(), any(), any())).thenReturn(List.of(7001L));
    doAnswer(
            invocation -> {
              sentInTransaction.add(inTransaction.get());
              return null;
            })
        .when(emailDelivery)
        .send(any());
  }

  @Test
  @DisplayName("用户名为空：422 username/REQUIRED，不查库")
  void should_reject_blank_username_with_required() {
    assertThatThrownBy(() -> handler.handle(command("  ")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(tuple("username", "REQUIRED")));
    verifyNoInteractions(users, reauthenticationGuard);
  }

  @Test
  @DisplayName("用户名不一致：422 username/MISMATCH，不弹再认证")
  void should_reject_mismatched_username_before_reauth() {
    assertThatThrownBy(() -> handler.handle(command("someone-else")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(tuple("username", "MISMATCH")));
    verifyNoInteractions(reauthenticationGuard);
  }

  @Test
  @DisplayName("用户名比对不分大小写、忽略首尾空白")
  void should_accept_username_ignoring_case_and_surrounding_spaces() {
    AccountDeletionRequested result = handler.handle(command("  Chen-Yu "));

    assertThat(result.deletionDueAt()).isEqualTo(NOW.plus(COOLING));
  }

  @Test
  @DisplayName("已在冷静期：409 PENDING_DELETION，不弹再认证")
  void should_reject_when_already_pending_deletion() {
    when(users.findById(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(() -> handler.handle(command("chen-yu")))
        .isInstanceOf(UserPendingDeletionException.class);
    verifyNoInteractions(reauthenticationGuard);
  }

  @Test
  @DisplayName("已封禁（理论上没有会话，并发撞上时）：401，不弹再认证、什么都不改")
  void should_require_authentication_for_banned_user() {
    when(users.findById(42L))
        .thenAnswer(
            invocation ->
                Optional.of(
                    User.restore()
                        .id(42L)
                        .email(EMAIL)
                        .username(Username.of("chen-yu"))
                        .status(UserStatus.BANNED)
                        .bannedAt(NOW)
                        .version(0L)
                        .build()));

    assertThatThrownBy(() -> handler.handle(command("chen-yu")))
        .isInstanceOf(AuthenticationRequiredException.class);
    verify(users, never()).save(any());
    verifyNoInteractions(reauthenticationGuard, sessionTerminator, tokens, emailDelivery);
  }

  @Test
  @DisplayName("再认证超时：403 原样抛出，什么都不改")
  void should_propagate_reauth_required_without_changes() {
    doThrow(new ReauthenticationRequiredException())
        .when(reauthenticationGuard)
        .requireRecent(CURRENT);

    assertThatThrownBy(() -> handler.handle(command("chen-yu")))
        .isInstanceOf(ReauthenticationRequiredException.class);
    verify(users, never()).save(any());
    verifyNoInteractions(sessionTerminator, tokens, emailDelivery);
  }

  @Test
  @DisplayName("发起成功：进冷静期、作废令牌（撤销令牌不动）、撤销全部会话，同一事务里登记「注销已提交」，返回到期时间")
  void should_request_deletion_revoke_tokens_and_all_sessions() {
    AccountDeletionRequested result = handler.handle(command("chen-yu"));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.PENDING_DELETION);
    assertThat(saved.getValue().getDeletionDueAt()).isEqualTo(NOW.plus(COOLING));
    verify(tokens).revokeAllActive(42L, NOW);
    verify(tokens, never())
        .revokeActive(anyLong(), eq(EmailTokenPurpose.EMAIL_CHANGE_REVERT), any());
    verify(sessionTerminator).terminateAll(42L, LoginEndReason.DELETION_REQUESTED, NOW);
    InOrder order = inOrder(sessionTerminator, emailDelivery);
    order.verify(sessionTerminator).terminateAll(anyLong(), any(), any());
    ArgumentCaptor<OutgoingMail> mail = ArgumentCaptor.forClass(OutgoingMail.class);
    order.verify(emailDelivery).send(mail.capture());
    assertThat(mail.getValue()).isInstanceOf(AccountDeletionRequestedMail.class);
    AccountDeletionRequestedMail requested = (AccountDeletionRequestedMail) mail.getValue();
    assertThat(requested.to()).isEqualTo(EMAIL);
    assertThat(requested.displayName()).isEqualTo("陈宇");
    assertThat(requested.deletionDueAt()).isEqualTo(NOW.plus(COOLING));
    assertThat(requested.notice().device()).isEqualTo("Chrome 141 · macOS");
    assertThat(sentInTransaction).containsExactly(true);
    assertThat(result.deletionDueAt()).isEqualTo(NOW.plus(COOLING));
  }

  @Test
  @DisplayName("登记失败：异常原样抛出，注销随事务回滚")
  void should_fail_when_registration_fails() {
    IllegalStateException boom = new IllegalStateException("登记表写入失败");
    doThrow(boom).when(emailDelivery).send(any());

    assertThatThrownBy(() -> handler.handle(command("chen-yu"))).isSameAs(boom);
  }

  /// 带客户端信息的命令。
  ///
  /// @param username 输入的用户名
  /// @return 命令
  private static RequestAccountDeletionCommand command(String username) {
    return RequestAccountDeletionCommand.of(username, "203.0.113.9", "Mozilla/5.0");
  }

  /// 指定状态的用户。
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
        ? builder.deletionDueAt(NOW.plus(COOLING)).build()
        : builder.build();
  }

  /// 记录事务边界的事务操作：回调同步执行。
  ///
  /// @param inTransaction 事务标记
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
