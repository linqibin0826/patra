package dev.patra.identity.app.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.exception.CurrentSessionRevocationException;
import dev.patra.identity.domain.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.model.enums.DeviceClass;
import dev.patra.identity.domain.model.enums.LoginEndReason;
import dev.patra.identity.domain.model.vo.DeviceId;
import dev.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.port.throttle.NewDeviceMailThrottlePort;
import dev.patra.identity.domain.service.ReauthenticationGuard;
import dev.patra.identity.domain.service.SessionTerminator;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// RevokeSessionHandler 单元测试：踢单个设备（PAP-99 设计第 11.2 节）。
@DisplayName("RevokeSessionHandler 单元测试")
class RevokeSessionHandlerTest {

  static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  static final CurrentUser CURRENT = CurrentUser.of(42L, 7002L, AccountType.USER, ClientType.WEB);

  static final DeviceId DEVICE = new DeviceId("Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy");

  private final List<String> events = new ArrayList<>();
  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final ReauthenticationGuard guard = mock(ReauthenticationGuard.class);
  private final SessionTerminator terminator = mock(SessionTerminator.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final NewDeviceMailThrottlePort newDeviceThrottle = mock(NewDeviceMailThrottlePort.class);
  private final RevokeSessionHandler handler =
      new RevokeSessionHandler(
          currentUserPort,
          guard,
          terminator,
          records,
          newDeviceThrottle,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubCurrentUser() {
    when(currentUserPort.require()).thenReturn(CURRENT);
  }

  @Test
  @DisplayName("踢本机：409 CURRENT_SESSION，先于再认证")
  void should_refuse_current_session_before_reauth() {
    assertThatThrownBy(() -> handler.handle(RevokeSessionCommand.of(7002L)))
        .isInstanceOf(CurrentSessionRevocationException.class);

    verifyNoInteractions(guard, terminator);
  }

  @Test
  @DisplayName("再认证超时：403，不撤销")
  void should_require_recent_reauth() {
    doThrow(new ReauthenticationRequiredException()).when(guard).requireRecent(CURRENT);

    assertThatThrownBy(() -> handler.handle(RevokeSessionCommand.of(7001L)))
        .isInstanceOf(ReauthenticationRequiredException.class);

    verifyNoInteractions(terminator);
  }

  @Test
  @DisplayName("踢掉：先过再认证，再以 REVOKED 结束；返回 1")
  void should_terminate_with_revoked_reason() {
    when(terminator.terminate(42L, 7001L, LoginEndReason.REVOKED, NOW)).thenReturn(true);

    SessionsRevoked revoked = handler.handle(RevokeSessionCommand.of(7001L));

    assertThat(revoked.revokedSessions()).isEqualTo(1);
    InOrder order = inOrder(guard, terminator);
    order.verify(guard).requireRecent(CURRENT);
    order.verify(terminator).terminate(42L, 7001L, LoginEndReason.REVOKED, NOW);
  }

  @Test
  @DisplayName("会话已经不在：返回 0，不报错，不动提醒冷却")
  void should_return_zero_when_session_already_gone() {
    when(terminator.terminate(42L, 7001L, LoginEndReason.REVOKED, NOW)).thenReturn(false);

    assertThat(handler.handle(RevokeSessionCommand.of(7001L)).revokedSessions()).isZero();
    verify(newDeviceThrottle, never()).reset(42L, DEVICE);
  }

  @Test
  @DisplayName("踢掉之后清掉这台设备的新设备提醒冷却：它再登录要立即提醒")
  void should_reset_new_device_mail_cooldown_of_kicked_device() {
    when(terminator.terminate(42L, 7001L, LoginEndReason.REVOKED, NOW)).thenReturn(true);
    when(records.findById(7001L)).thenReturn(Optional.of(kickedRecord(7001L)));

    handler.handle(RevokeSessionCommand.of(7001L));

    InOrder order = inOrder(terminator, newDeviceThrottle);
    order.verify(terminator).terminate(42L, 7001L, LoginEndReason.REVOKED, NOW);
    order.verify(newDeviceThrottle).reset(42L, DEVICE);
  }

  /// 一条被踢掉的、带设备标识的登录记录。
  ///
  /// @param id 记录 ID
  /// @return 记录
  static UserLoginRecord kickedRecord(long id) {
    return UserLoginRecord.restore()
        .id(id)
        .userId(42L)
        .clientType(ClientType.WEB)
        .deviceId(DEVICE)
        .deviceClass(DeviceClass.DESKTOP)
        .expiresAt(NOW.plusSeconds(3600))
        .endedAt(NOW)
        .endReason(LoginEndReason.REVOKED)
        .version(1L)
        .createdAt(NOW.minusSeconds(60))
        .updatedAt(NOW)
        .build();
  }

  /// 撤销会话在事务里，清提醒冷却在提交之后。
  @Test
  @DisplayName("撤销会话在事务里，清提醒冷却（与会话无关的 Redis 写入）在提交之后")
  void should_reset_cooldown_after_commit() {
    when(terminator.terminate(42L, 7001L, LoginEndReason.REVOKED, NOW))
        .thenAnswer(
            inv -> {
              events.add("terminate");
              return true;
            });
    when(records.findById(7001L)).thenReturn(Optional.of(kickedRecord(7001L)));
    doAnswer(inv -> events.add("reset")).when(newDeviceThrottle).reset(42L, DEVICE);

    handler.handle(RevokeSessionCommand.of(7001L));

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN, "terminate", RecordingTransactions.END, "reset");
  }
}
