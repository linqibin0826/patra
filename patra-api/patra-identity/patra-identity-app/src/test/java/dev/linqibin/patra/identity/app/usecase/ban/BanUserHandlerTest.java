package dev.linqibin.patra.identity.app.usecase.ban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// BanUserHandler 单元测试。
@DisplayName("BanUserHandler 单元测试")
class BanUserHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-06T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");

  private final UserRepository users = mock(UserRepository.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final BanUserHandler handler =
      new BanUserHandler(users, sessions, records, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("封禁正常用户：状态改为封禁，封禁时间取时钟")
  void should_ban_active_user() {
    when(users.findById(42L))
        .thenReturn(Optional.of(User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null)));
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
    when(users.findById(42L))
        .thenReturn(
            Optional.of(User.restore(42L, EMAIL, UserStatus.BANNED, earlier, 1L, null, null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(BanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getBannedAt()).isEqualTo(earlier);
  }

  @Test
  @DisplayName("封禁后删掉该用户的全部会话，被删会话的记录标成 BANNED；找不到的记录跳过")
  void should_revoke_all_sessions_and_end_records() {
    when(users.findById(42L))
        .thenReturn(Optional.of(User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of(7001L, 7002L));
    UserLoginRecord first =
        UserLoginRecord.restore(
            7001L, 42L, ClientType.WEB, null, NOW.plusSeconds(1), null, null, 0L, NOW, NOW);
    when(records.findById(7001L)).thenReturn(Optional.of(first));
    when(records.findById(7002L)).thenReturn(Optional.empty());

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
    when(users.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(BanUserCommand.of(42L)))
        .isInstanceOf(UserNotFoundException.class);
    verifyNoInteractions(sessions, records);
  }
}
