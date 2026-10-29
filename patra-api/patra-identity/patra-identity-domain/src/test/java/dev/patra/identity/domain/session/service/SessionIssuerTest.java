package dev.patra.identity.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.model.vo.NewUserSession;
import dev.patra.identity.domain.session.policy.SessionLifetime;
import dev.patra.identity.domain.session.policy.SessionLifetimePolicy;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/// SessionIssuer 单元测试。
@DisplayName("SessionIssuer 单元测试")
class SessionIssuerTest {

  private static final ClientOrigin ORIGIN =
      ClientOrigin.builder()
          .ip("203.0.113.9")
          .userAgent("Mozilla/5.0 Chrome")
          .deviceName("Chrome 141 · macOS")
          .deviceClass(DeviceClass.DESKTOP)
          .ipRegion("澳大利亚")
          .build();

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
  private static final SessionLifetime WEB =
      SessionLifetime.of(Duration.ofDays(30), Duration.ofDays(180));
  private static final SessionLifetimePolicy POLICY =
      SessionLifetimePolicy.of(Map.of(ClientType.WEB, WEB), 10);

  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final SessionIssuer issuer =
      new SessionIssuer(records, sessions, POLICY, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("先存登录记录拿 ID，再用它建会话，返回令牌")
  void should_save_record_then_issue_session() {
    when(records.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 7001L));
    when(sessions.issue(any())).thenReturn(IssuedUserSession.of("patra_user_x", List.of()));

    IssuedUserSession issued =
        issuer.issue(
            42L, LoginClient.of("web", "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy"), ORIGIN);

    assertThat(issued.token()).isEqualTo("patra_user_x");
    ArgumentCaptor<UserLoginRecord> saved = ArgumentCaptor.forClass(UserLoginRecord.class);
    ArgumentCaptor<NewUserSession> requested = ArgumentCaptor.forClass(NewUserSession.class);
    InOrder order = inOrder(records, sessions);
    order.verify(records).save(saved.capture());
    order.verify(sessions).issue(requested.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(42L);
    assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
    NewUserSession session = requested.getValue();
    assertThat(session.userId()).isEqualTo(42L);
    assertThat(session.sessionId()).isEqualTo(7001L);
    assertThat(session.accountType()).isEqualTo(AccountType.USER);
    assertThat(session.client().device()).isPresent();
    assertThat(session.now()).isEqualTo(NOW);
    assertThat(session.lifetime()).isEqualTo(WEB);
    assertThat(session.maxSessionsPerUser()).isEqualTo(10);
    assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
  }

  @Test
  @DisplayName("被挤掉的会话：对应记录标成 REPLACED 并保存；找不到的跳过")
  void should_end_replaced_records() {
    when(records.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 7001L));
    when(sessions.issue(any()))
        .thenReturn(IssuedUserSession.of("patra_user_x", List.of(6001L, 6002L)));
    UserLoginRecord replaced =
        UserLoginRecord.restore()
            .id(6001L)
            .userId(42L)
            .clientType(ClientType.WEB)
            .deviceClass(DeviceClass.UNKNOWN)
            .expiresAt(NOW.plusSeconds(1))
            .version(0L)
            .createdAt(NOW)
            .updatedAt(NOW)
            .build();
    when(records.findById(6001L)).thenReturn(Optional.of(replaced));
    when(records.findById(6002L)).thenReturn(Optional.empty());

    issuer.issue(42L, LoginClient.web(), ORIGIN);

    assertThat(replaced.getEndReason()).isEqualTo(LoginEndReason.REPLACED);
    assertThat(replaced.getEndedAt()).isEqualTo(NOW);
    verify(records).save(replaced);
  }

  @Test
  @DisplayName("客户端类型没配有效期：抛 IllegalStateException，什么都不写")
  void should_fail_before_writing_when_lifetime_is_missing() {
    SessionLifetimePolicy policy = mock(SessionLifetimePolicy.class);
    when(policy.lifetimeFor(ClientType.WEB)).thenThrow(new IllegalStateException("没配"));
    SessionIssuer broken =
        new SessionIssuer(records, sessions, policy, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> broken.issue(42L, LoginClient.web(), ORIGIN))
        .isInstanceOf(IllegalStateException.class);
    verify(records, never()).save(any());
    verify(sessions, never()).issue(any());
  }

  /// 模拟仓储分配 ID：按原记录的内容恢复一条带 ID 的。
  ///
  /// @param login 未保存的记录
  /// @param id 分配的 ID
  /// @return 带 ID 的记录
  private static UserLoginRecord withId(UserLoginRecord login, long id) {
    return UserLoginRecord.restore()
        .id(id)
        .userId(login.getUserId())
        .clientType(login.getClientType())
        .deviceId(login.getDeviceId().orElse(null))
        .userAgent(login.getUserAgent())
        .deviceName(login.getDeviceName())
        .deviceClass(login.getDeviceClass())
        .clientIp(login.getClientIp())
        .ipRegion(login.getIpRegion())
        .expiresAt(login.getExpiresAt())
        .endedAt(login.getEndedAt())
        .endReason(login.getEndReason())
        .version(0L)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }

  @Test
  @DisplayName("登录记录带上请求来源")
  void should_start_record_with_origin() {
    when(records.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 7001L));
    when(sessions.issue(any())).thenReturn(IssuedUserSession.of("patra_user_x", List.of()));

    issuer.issue(42L, LoginClient.web(), ORIGIN);

    ArgumentCaptor<UserLoginRecord> saved = ArgumentCaptor.forClass(UserLoginRecord.class);
    verify(records).save(saved.capture());
    assertThat(saved.getValue().getClientIp()).isEqualTo("203.0.113.9");
    assertThat(saved.getValue().getDeviceName()).isEqualTo("Chrome 141 · macOS");
    assertThat(saved.getValue().getDeviceClass()).isEqualTo(DeviceClass.DESKTOP);
    assertThat(saved.getValue().getIpRegion()).isEqualTo("澳大利亚");
    assertThat(saved.getValue().getUserAgent()).isEqualTo("Mozilla/5.0 Chrome");
  }
}
