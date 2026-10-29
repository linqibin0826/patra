package dev.patra.identity.app.session.usecase.revoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.throttle.NewDeviceMailThrottlePort;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.identity.domain.session.service.SessionTerminator;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// RevokeOtherSessionsHandler 单元测试：踢除本机外的全部设备（PAP-99 设计第 11.3 节）。
@DisplayName("RevokeOtherSessionsHandler 单元测试")
class RevokeOtherSessionsHandlerTest {

  static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  static final CurrentUser CURRENT = CurrentUser.of(42L, 7002L, AccountType.USER, ClientType.WEB);

  private final List<String> events = new ArrayList<>();
  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final ReauthenticationGuard guard = mock(ReauthenticationGuard.class);
  private final SessionTerminator terminator = mock(SessionTerminator.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final NewDeviceMailThrottlePort newDeviceThrottle = mock(NewDeviceMailThrottlePort.class);
  private final RevokeOtherSessionsHandler handler =
      new RevokeOtherSessionsHandler(
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
  @DisplayName("先过再认证，再保留本机、其余以 REVOKED 结束；返回撤销数")
  void should_terminate_all_but_current_after_reauth() {
    when(terminator.terminateAllExcept(42L, 7002L, LoginEndReason.REVOKED, NOW))
        .thenReturn(List.of(7001L, 7003L));

    SessionsRevoked revoked = handler.handle(RevokeOtherSessionsCommand.of());

    assertThat(revoked.revokedSessions()).isEqualTo(2);
    InOrder order = inOrder(guard, terminator);
    order.verify(guard).requireRecent(CURRENT);
    order.verify(terminator).terminateAllExcept(42L, 7002L, LoginEndReason.REVOKED, NOW);
  }

  @Test
  @DisplayName("再认证超时：403，不撤销")
  void should_require_recent_reauth() {
    doThrow(new ReauthenticationRequiredException()).when(guard).requireRecent(CURRENT);

    assertThatThrownBy(() -> handler.handle(RevokeOtherSessionsCommand.of()))
        .isInstanceOf(ReauthenticationRequiredException.class);

    verifyNoInteractions(terminator);
  }

  @Test
  @DisplayName("踢掉之后清掉每台被踢设备的新设备提醒冷却，没有设备标识的清「无设备」桶")
  void should_reset_new_device_mail_cooldown_of_every_kicked_device() {
    DeviceId device = new DeviceId("Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy");
    when(terminator.terminateAllExcept(42L, 7002L, LoginEndReason.REVOKED, NOW))
        .thenReturn(List.of(7001L, 7003L));
    when(records.findAllByIds(List.of(7001L, 7003L)))
        .thenReturn(List.of(kickedRecord(7001L, device), kickedRecord(7003L, null)));

    handler.handle(RevokeOtherSessionsCommand.of());

    verify(newDeviceThrottle).reset(42L, device);
    verify(newDeviceThrottle).reset(42L, null);
  }

  /// 撤销会话在事务里，清提醒冷却在提交之后。
  @Test
  @DisplayName("撤销会话在事务里，清提醒冷却（与会话无关的 Redis 写入）在提交之后")
  void should_reset_cooldowns_after_commit() {
    when(terminator.terminateAllExcept(42L, 7002L, LoginEndReason.REVOKED, NOW))
        .thenAnswer(
            inv -> {
              events.add("terminate");
              return List.of(7001L);
            });
    when(records.findAllByIds(List.of(7001L))).thenReturn(List.of(kickedRecord(7001L, null)));
    doAnswer(inv -> events.add("reset")).when(newDeviceThrottle).reset(42L, null);

    handler.handle(RevokeOtherSessionsCommand.of());

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN, "terminate", RecordingTransactions.END, "reset");
  }

  /// 一条被踢掉的登录记录。
  ///
  /// @param id 记录 ID
  /// @param device 设备标识，可以为 `null`
  /// @return 记录
  static UserLoginRecord kickedRecord(long id, DeviceId device) {
    return UserLoginRecord.restore()
        .id(id)
        .userId(42L)
        .clientType(ClientType.WEB)
        .deviceId(device)
        .deviceClass(DeviceClass.DESKTOP)
        .expiresAt(NOW.plusSeconds(3600))
        .endedAt(NOW)
        .endReason(LoginEndReason.REVOKED)
        .version(1L)
        .createdAt(NOW.minusSeconds(60))
        .updatedAt(NOW)
        .build();
  }
}
