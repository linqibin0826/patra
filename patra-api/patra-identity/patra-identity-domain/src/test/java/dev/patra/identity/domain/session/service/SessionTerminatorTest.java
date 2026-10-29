package dev.patra.identity.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SessionTerminator 单元测试：先撤销 Redis 会话，再按原因结束登录记录（PAP-99 设计第 9.3 节）。
@DisplayName("SessionTerminator 单元测试")
class SessionTerminatorTest {

  static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final SessionTerminator terminator = new SessionTerminator(sessions, records);

  @Test
  @DisplayName("撤销其余会话：被撤销的记录按原因结束并保存")
  void should_end_records_of_revoked_sessions_with_reason() {
    UserLoginRecord first = open(7001L);
    when(sessions.revokeAllExcept(AccountType.USER, 42L, 7000L)).thenReturn(List.of(7001L));
    when(records.findAllByIds(List.of(7001L))).thenReturn(List.of(first));

    List<Long> ended =
        terminator.terminateAllExcept(42L, 7000L, LoginEndReason.PASSWORD_CHANGED, NOW);

    assertThat(ended).containsExactly(7001L);
    assertThat(first.getEndReason()).isEqualTo(LoginEndReason.PASSWORD_CHANGED);
    assertThat(first.getEndedAt()).isEqualTo(NOW);
    verify(records).save(first);
  }

  @Test
  @DisplayName("一次批量取出全部被撤销的记录，不逐条查")
  void should_load_revoked_records_in_one_query() {
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of(7001L, 7002L, 7003L));
    when(records.findAllByIds(List.of(7001L, 7002L, 7003L)))
        .thenReturn(List.of(open(7001L), open(7002L), open(7003L)));

    terminator.terminateAll(42L, LoginEndReason.PASSWORD_RESET, NOW);

    verify(records).findAllByIds(List.of(7001L, 7002L, 7003L));
    verify(records, never()).findById(anyLong());
    verify(records, times(3)).save(any(UserLoginRecord.class));
  }

  @Test
  @DisplayName("没有被撤销的会话：不查记录")
  void should_not_query_records_when_nothing_revoked() {
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of());

    assertThat(terminator.terminateAll(42L, LoginEndReason.BANNED, NOW)).isEmpty();

    verifyNoInteractions(records);
  }

  @Test
  @DisplayName("撤销全部会话：记录缺失的跳过")
  void should_skip_missing_records() {
    UserLoginRecord present = open(7002L);
    when(sessions.revokeAll(AccountType.USER, 42L)).thenReturn(List.of(7001L, 7002L));
    when(records.findAllByIds(List.of(7001L, 7002L))).thenReturn(List.of(present));

    assertThat(terminator.terminateAll(42L, LoginEndReason.BANNED, NOW))
        .containsExactly(7001L, 7002L);

    assertThat(present.getEndReason()).isEqualTo(LoginEndReason.BANNED);
    verify(records).save(present);
  }

  @Test
  @DisplayName("已经结束的记录保留第一次的原因")
  void should_keep_first_end_reason() {
    UserLoginRecord ended = open(7003L);
    ended.end(LoginEndReason.LOGOUT, NOW.minusSeconds(5));
    when(sessions.revoke(AccountType.USER, 42L, 7003L)).thenReturn(true);
    when(records.findAllByIds(List.of(7003L))).thenReturn(List.of(ended));

    assertThat(terminator.terminate(42L, 7003L, LoginEndReason.REVOKED, NOW)).isTrue();

    assertThat(ended.getEndReason()).isEqualTo(LoginEndReason.LOGOUT);
  }

  @Test
  @DisplayName("会话已经不在：返回 false，不碰记录")
  void should_not_touch_record_when_session_already_gone() {
    when(sessions.revoke(AccountType.USER, 42L, 7004L)).thenReturn(false);

    assertThat(terminator.terminate(42L, 7004L, LoginEndReason.REVOKED, NOW)).isFalse();

    verifyNoInteractions(records);
  }

  @Test
  @DisplayName("Redis 失败：异常原样传出，不碰记录")
  void should_not_touch_records_when_redis_fails() {
    when(sessions.revokeAll(AccountType.USER, 42L)).thenThrow(new IllegalStateException("down"));

    assertThatThrownBy(() -> terminator.terminateAll(42L, LoginEndReason.PASSWORD_RESET, NOW))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(records);
    verify(sessions, never()).revoke(any(), any(Long.class), any(Long.class));
  }

  /// 一条未结束的登录记录。
  ///
  /// @param id 记录 ID
  /// @return 记录
  private static UserLoginRecord open(long id) {
    return UserLoginRecord.restore()
        .id(id)
        .userId(42L)
        .clientType(ClientType.WEB)
        .deviceClass(DeviceClass.UNKNOWN)
        .expiresAt(NOW.plusSeconds(3600))
        .version(0L)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}
