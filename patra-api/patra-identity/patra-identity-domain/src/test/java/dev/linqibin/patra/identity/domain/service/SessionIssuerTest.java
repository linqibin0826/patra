package dev.linqibin.patra.identity.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import dev.linqibin.patra.identity.domain.policy.SessionLifetimePolicy;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.session.NewUserSession;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
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

    IssuedUserSession issued = issuer.issue(42L, LoginClient.of("web", "mac-safari"));

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
        UserLoginRecord.restore(
            6001L, 42L, ClientType.WEB, null, NOW.plusSeconds(1), null, null, 0L, NOW, NOW);
    when(records.findById(6001L)).thenReturn(Optional.of(replaced));
    when(records.findById(6002L)).thenReturn(Optional.empty());

    issuer.issue(42L, LoginClient.web());

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

    assertThatThrownBy(() -> broken.issue(42L, LoginClient.web()))
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
    return UserLoginRecord.restore(
        id,
        login.getUserId(),
        login.getClientType(),
        login.getDeviceId().orElse(null),
        login.getExpiresAt(),
        login.getEndedAt(),
        login.getEndReason(),
        0L,
        NOW,
        NOW);
  }
}
