package dev.linqibin.patra.identity.infra.adapter.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.policy.SessionLifetime;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.session.NewUserSession;
import dev.linqibin.patra.identity.session.IssuedSession;
import dev.linqibin.patra.identity.session.NewSession;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.identity.session.SessionToken;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// SessionStoreAdapter 单元测试：domain 的记录怎么转成会话模块的类型。
@DisplayName("SessionStoreAdapter 单元测试")
class SessionStoreAdapterTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

  private final RedisSessionStore store = mock(RedisSessionStore.class);
  private final SessionStoreAdapter adapter = new SessionStoreAdapter(store);

  @Test
  @DisplayName("签发：时间按 now 算，设备标识、有效期、上限原样带过去；返回令牌原文和被挤掉的 ID")
  void should_translate_new_session_and_result() {
    SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());
    when(store.create(any())).thenReturn(IssuedSession.of(token, List.of(6001L)));

    IssuedUserSession issued =
        adapter.issue(
            NewUserSession.builder()
                .userId(42L)
                .sessionId(7001L)
                .accountType(AccountType.USER)
                .client(LoginClient.of("web", "mac-safari"))
                .now(NOW)
                .lifetime(SessionLifetime.of(Duration.ofDays(30), Duration.ofDays(180)))
                .maxSessionsPerUser(10)
                .build());

    assertThat(issued.token()).isEqualTo(token.value());
    assertThat(issued.replacedSessionIds()).containsExactly(6001L);
    ArgumentCaptor<NewSession> created = ArgumentCaptor.forClass(NewSession.class);
    verify(store).create(created.capture());
    NewSession session = created.getValue();
    assertThat(session.userId()).isEqualTo(42L);
    assertThat(session.sessionId()).isEqualTo(7001L);
    assertThat(session.accountType()).isEqualTo(AccountType.USER);
    assertThat(session.clientType()).isEqualTo(ClientType.WEB);
    assertThat(session.deviceId()).isEqualTo("mac-safari");
    assertThat(session.createdAt()).isEqualTo(NOW);
    assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(180)));
    assertThat(session.idleTimeout()).isEqualTo(Duration.ofDays(30));
    assertThat(session.maxSessionsPerUser()).isEqualTo(10);
  }

  @Test
  @DisplayName("撤销一条和撤销全部直接转给存储")
  void should_delegate_revocations() {
    when(store.delete(AccountType.USER, 42L, 7001L)).thenReturn(true);
    when(store.deleteAll(AccountType.USER, 42L)).thenReturn(List.of(7001L, 7002L));

    assertThat(adapter.revoke(AccountType.USER, 42L, 7001L)).isTrue();
    assertThat(adapter.revokeAll(AccountType.USER, 42L)).containsExactly(7001L, 7002L);
  }
}
