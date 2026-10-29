package dev.patra.identity.app.usecase.ban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.exception.UserNotFoundException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.model.enums.DeviceClass;
import dev.patra.identity.domain.model.enums.LoginEndReason;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.identity.domain.port.session.SessionStorePort;
import dev.patra.identity.domain.service.SessionTerminator;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/// BanUserHandler 单元测试。
@DisplayName("BanUserHandler 单元测试")
class BanUserHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-06T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");

  private final List<String> events = new ArrayList<>();
  private final UserRepository users = mock(UserRepository.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final BanUserHandler handler =
      new BanUserHandler(
          users,
          new SessionTerminator(sessions, records),
          tokens,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("封禁正常用户：状态改为封禁，封禁时间取时钟")
  void should_ban_active_user() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(EMAIL)
                    .username(Username.of("chen-yu"))
                    .status(UserStatus.ACTIVE)
                    .version(0L)
                    .build()));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(BanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(saved.getValue().getBannedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("重复封禁保留原来的封禁时间")
  void should_keep_original_ban_time() {
    Instant earlier = Instant.parse("2026-10-01T00:00:00Z");
    when(users.findByIdForUpdate(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(EMAIL)
                    .username(Username.of("chen-yu"))
                    .status(UserStatus.BANNED)
                    .bannedAt(earlier)
                    .version(1L)
                    .build()));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(BanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getBannedAt()).isEqualTo(earlier);
  }

  @Test
  @DisplayName("封禁后删掉该用户的全部会话，被删会话的记录标成 BANNED；找不到的记录跳过")
  void should_revoke_all_sessions_and_end_records() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(EMAIL)
                    .username(Username.of("chen-yu"))
                    .status(UserStatus.ACTIVE)
                    .version(0L)
                    .build()));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of(7001L, 7002L));
    UserLoginRecord first =
        UserLoginRecord.restore()
            .id(7001L)
            .userId(42L)
            .clientType(ClientType.WEB)
            .deviceClass(DeviceClass.UNKNOWN)
            .expiresAt(NOW.plusSeconds(1))
            .version(0L)
            .createdAt(NOW)
            .updatedAt(NOW)
            .build();
    when(records.findAllByIds(List.of(7001L, 7002L))).thenReturn(List.of(first));

    handler.handle(BanUserCommand.of(42L));

    var order = inOrder(users, sessions);
    order.verify(users).save(any());
    order.verify(sessions).revokeAll(AccountType.USER, 42L);
    assertThat(first.getEndReason()).isEqualTo(LoginEndReason.BANNED);
    assertThat(first.getEndedAt()).isEqualTo(NOW);
    verify(records).save(first);
  }

  @Test
  @DisplayName("用户不存在：404，不碰会话")
  void should_not_touch_sessions_when_user_is_missing() {
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(BanUserCommand.of(42L)))
        .isInstanceOf(UserNotFoundException.class);
    verifyNoInteractions(sessions, records);
  }

  @Test
  @DisplayName("封禁顺带作废该用户全部有效令牌（改邮箱撤销令牌除外，由仓储保证），在保存用户之后")
  void should_revoke_all_email_tokens_when_banning() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(EMAIL)
                    .username(Username.of("chen-yu"))
                    .status(UserStatus.ACTIVE)
                    .version(0L)
                    .build()));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(BanUserCommand.of(42L));

    InOrder order = inOrder(users, tokens);
    order.verify(users).save(any());
    order.verify(tokens).revokeAllActive(42L, NOW);
  }

  /// 撤销会话在锁住用户行的同一事务里：Redis 失败时封禁回滚，并挡住并发登录。
  @Test
  @DisplayName("撤销会话在锁住用户行的同一事务里：Redis 失败时封禁回滚，挡住并发登录")
  void should_terminate_sessions_inside_transaction() {
    when(users.findByIdForUpdate(42L))
        .thenAnswer(
            inv -> {
              events.add("lock");
              return Optional.of(
                  User.restore()
                      .id(42L)
                      .email(EMAIL)
                      .username(Username.of("chen-yu"))
                      .status(UserStatus.ACTIVE)
                      .version(0L)
                      .build());
            });
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.revokeAll(AccountType.USER, 42L))
        .thenAnswer(
            inv -> {
              events.add("revokeSessions");
              return List.of();
            });

    handler.handle(BanUserCommand.of(42L));

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN, "lock", "revokeSessions", RecordingTransactions.END);
  }
}
