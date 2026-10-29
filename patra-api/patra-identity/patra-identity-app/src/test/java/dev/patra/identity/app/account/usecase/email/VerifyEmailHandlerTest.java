package dev.patra.identity.app.account.usecase.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

/// VerifyEmailHandler 单元测试：加锁顺序、条件消费、邮箱核对、会话撤销、幂等。
@DisplayName("VerifyEmailHandler 单元测试")
class VerifyEmailHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("D".repeat(43));
  private static final EmailToken TOKEN =
      EmailToken.restore()
          .id(900L)
          .userId(42L)
          .purpose(EmailTokenPurpose.EMAIL_VERIFY)
          .email(EMAIL)
          .tokenHash(SECRET.hash())
          .expiresAt(NOW.plus(Duration.ofHours(23)))
          .version(0L)
          .build();

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final VerifyEmailHandler handler =
      new VerifyEmailHandler(
          currentUserPort,
          tokens,
          users,
          new SessionTerminator(sessions, records),
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 默认：令牌存在且可消费，用户 42 未验证，当前用户是 42 的会话 7。
  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.current())
        .thenReturn(Optional.of(CurrentUser.of(42L, 7L, AccountType.USER, ClientType.WEB)));
    when(tokens.findByTokenHash(SECRET.hash())).thenReturn(Optional.of(TOKEN));
    when(tokens.consume(SECRET.hash(), EmailTokenPurpose.EMAIL_VERIFY, NOW))
        .thenReturn(Optional.of(consumed()));
    when(users.findByIdForUpdate(42L)).thenAnswer(invocation -> Optional.of(user(EMAIL, null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.revokeAllExcept(AccountType.USER, 42L, 7L)).thenReturn(List.of(5L, 6L));
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of(5L, 6L, 7L));
    when(records.findAllByIds(anyCollection()))
        .thenAnswer(
            invocation ->
                invocation.<Collection<Long>>getArgument(0).stream()
                    .map(VerifyEmailHandlerTest::openLogin)
                    .toList());
    when(records.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("顺序：按哈希无锁查 → 锁用户行 → 条件消费")
  void should_lock_user_before_consuming_token() {
    handler.handle(VerifyEmailCommand.of(SECRET.value()));

    InOrder order = inOrder(tokens, users);
    order.verify(tokens).findByTokenHash(SECRET.hash());
    order.verify(users).findByIdForUpdate(42L);
    order.verify(tokens).consume(SECRET.hash(), EmailTokenPurpose.EMAIL_VERIFY, NOW);
  }

  @Test
  @DisplayName("本人带会话来验证：写验证时间，保留当前会话、撤销其余并把记录标 REVOKED")
  void should_verify_and_keep_current_session_but_revoke_others() {
    EmailVerified result = handler.handle(VerifyEmailCommand.of(SECRET.value()));

    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    assertThat(result.emailVerifiedAt()).isEqualTo(NOW);
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().isEmailVerified()).isTrue();
    assertThat(saved.getValue().getEmailVerifiedAt()).isEqualTo(NOW);
    verify(sessions).revokeAllExcept(AccountType.USER, 42L, 7L);
    verify(sessions, never()).revokeAll(any(), anyLong());
    ArgumentCaptor<UserLoginRecord> ended = ArgumentCaptor.forClass(UserLoginRecord.class);
    verify(records, times(2)).save(ended.capture());
    assertThat(ended.getAllValues())
        .extracting(UserLoginRecord::getId, UserLoginRecord::getEndReason)
        .containsExactly(
            tuple(5L, LoginEndReason.EMAIL_VERIFIED), tuple(6L, LoginEndReason.EMAIL_VERIFIED));
  }

  @Test
  @DisplayName("匿名验证：撤销该用户全部会话")
  void should_revoke_all_sessions_when_anonymous() {
    when(currentUserPort.current()).thenReturn(Optional.empty());

    handler.handle(VerifyEmailCommand.of(SECRET.value()));

    verify(sessions).revokeAll(AccountType.USER, 42L);
    verify(sessions, never()).revokeAllExcept(any(), anyLong(), anyLong());
    verify(records, times(3)).save(any());
  }

  @Test
  @DisplayName("登录成别人去消费：只动令牌所属用户的会话，请求者的会话不受影响")
  void should_only_touch_sessions_of_token_owner_when_requester_differs() {
    when(currentUserPort.current())
        .thenReturn(Optional.of(CurrentUser.of(99L, 8L, AccountType.USER, ClientType.WEB)));

    handler.handle(VerifyEmailCommand.of(SECRET.value()));

    verify(sessions).revokeAll(AccountType.USER, 42L);
    verify(sessions, never()).revokeAllExcept(any(), anyLong(), anyLong());
    verify(sessions, never()).revokeAll(AccountType.USER, 99L);
    verify(users, never()).findByIdForUpdate(99L);
  }

  @Test
  @DisplayName("令牌为空：422 token / REQUIRED，不查库")
  void should_reject_blank_token_without_querying() {
    for (String blank : new String[] {"", "   ", null}) {
      assertThatThrownBy(() -> handler.handle(VerifyEmailCommand.of(blank)))
          .isInstanceOfSatisfying(
              InvalidUserFieldsException.class,
              e ->
                  assertThat(e.getFieldViolations())
                      .singleElement()
                      .satisfies(
                          v -> {
                            assertThat(v.field()).isEqualTo("token");
                            assertThat(v.code()).isEqualTo("REQUIRED");
                          }));
    }
    verifyNoInteractions(tokens, users, sessions);
  }

  @Test
  @DisplayName("令牌格式不对：422 token / INVALID，不查库")
  void should_reject_malformed_token_without_querying() {
    assertInvalid("short");
    assertInvalid("A".repeat(42) + "+");
    verifyNoInteractions(tokens, users, sessions);
  }

  @Test
  @DisplayName("令牌不存在：422 token / INVALID，不锁用户")
  void should_reject_unknown_token_without_locking_user() {
    when(tokens.findByTokenHash(SECRET.hash())).thenReturn(Optional.empty());

    assertInvalid(SECRET.value());
    verifyNoInteractions(users, sessions);
  }

  @Test
  @DisplayName("令牌用途不是验证邮箱：422 token / INVALID，不锁用户")
  void should_reject_token_of_other_purpose_without_locking_user() {
    EmailToken reset =
        EmailToken.restore()
            .id(901L)
            .userId(42L)
            .purpose(EmailTokenPurpose.PASSWORD_RESET)
            .email(EMAIL)
            .tokenHash(SECRET.hash())
            .expiresAt(NOW.plus(Duration.ofHours(1)))
            .version(0L)
            .build();
    when(tokens.findByTokenHash(SECRET.hash())).thenReturn(Optional.of(reset));

    assertInvalid(SECRET.value());
    verifyNoInteractions(users, sessions);
    verify(tokens, never()).consume(any(), any(), any());
  }

  @Test
  @DisplayName("锁后条件消费为空（已过期、被重发作废或被抢先消费）：422 token / INVALID，不写验证时间")
  void should_reject_when_consume_returns_empty_after_lock() {
    when(tokens.consume(SECRET.hash(), EmailTokenPurpose.EMAIL_VERIFY, NOW))
        .thenReturn(Optional.empty());

    assertInvalid(SECRET.value());
    verify(users, never()).save(any());
    verifyNoInteractions(sessions);
  }

  @Test
  @DisplayName("令牌的邮箱和用户当前邮箱不一致（期间改过邮箱）：422 token / INVALID，不写验证时间、不动会话")
  void should_reject_token_when_email_no_longer_matches() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(Optional.of(user(EmailAddress.of("other@example.com"), null)));

    assertInvalid(SECRET.value());
    verify(users, never()).save(any());
    verifyNoInteractions(sessions);
  }

  @Test
  @DisplayName("用户已经验证过：保留第一次的验证时间，仍撤销其他会话")
  void should_be_idempotent_for_already_verified_user() {
    Instant earlier = NOW.minus(Duration.ofDays(1));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(EMAIL, earlier)));

    EmailVerified result = handler.handle(VerifyEmailCommand.of(SECRET.value()));

    assertThat(result.emailVerifiedAt()).isEqualTo(earlier);
    verify(sessions).revokeAllExcept(AccountType.USER, 42L, 7L);
  }

  /// 断言某个输入被当成不可用的令牌。
  ///
  /// @param token 输入
  private void assertInvalid(String token) {
    assertThatThrownBy(() -> handler.handle(VerifyEmailCommand.of(token)))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .singleElement()
                    .satisfies(
                        v -> {
                          assertThat(v.field()).isEqualTo("token");
                          assertThat(v.code()).isEqualTo("INVALID");
                        }));
  }

  /// 消费之后的令牌。
  ///
  /// @return 令牌
  private static EmailToken consumed() {
    return EmailToken.restore()
        .id(900L)
        .userId(42L)
        .purpose(EmailTokenPurpose.EMAIL_VERIFY)
        .email(EMAIL)
        .tokenHash(SECRET.hash())
        .expiresAt(NOW.plus(Duration.ofHours(23)))
        .consumedAt(NOW)
        .version(1L)
        .build();
  }

  /// 一个正常用户。
  ///
  /// @param email 当前邮箱
  /// @param emailVerifiedAt 邮箱验证时间，可以为 `null`
  /// @return 用户
  private static User user(EmailAddress email, Instant emailVerifiedAt) {
    return User.restore()
        .id(42L)
        .email(email)
        .emailVerifiedAt(emailVerifiedAt)
        .username(Username.of("chen-yu"))
        .status(UserStatus.ACTIVE)
        .version(0L)
        .build();
  }

  /// 一条未结束的登录记录。
  ///
  /// @param id 记录 ID
  /// @return 记录
  private static UserLoginRecord openLogin(long id) {
    return UserLoginRecord.restore()
        .id(id)
        .userId(42L)
        .clientType(ClientType.WEB)
        .deviceClass(DeviceClass.UNKNOWN)
        .expiresAt(NOW.plus(Duration.ofDays(180)))
        .version(0L)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}
