package dev.patra.identity.infra.session.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.model.vo.ActiveUserSession;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.model.vo.NewUserSession;
import dev.patra.identity.domain.session.model.vo.SessionRotation;
import dev.patra.identity.domain.session.policy.SessionLifetime;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// SessionStoreAdapter 单元测试：domain 的记录怎么转成会话存储的类型。
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
                .client(LoginClient.of("web", "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy"))
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
    assertThat(session.deviceId()).isEqualTo("Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy");
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

  @Test
  @DisplayName("撤销除当前外的全部会话：原样委托，返回真的删掉了的 ID")
  void should_delegate_revoke_all_except() {
    when(store.deleteAllExcept(AccountType.USER, 42L, 7L)).thenReturn(List.of(5L, 6L));

    assertThat(adapter.revokeAllExcept(AccountType.USER, 42L, 7L)).containsExactly(5L, 6L);
  }

  @Test
  @DisplayName("换令牌：换了带新令牌原文，宽限期内没换是「没换」；会话不在为空；宽限期原样传给会话存储")
  void should_rotate_and_return_token_value() {
    SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());
    when(store.rotate(AccountType.USER, 42L, 7001L, Duration.ofSeconds(10)))
        .thenReturn(Optional.of(Rotation.rotated(token)));
    when(store.rotate(AccountType.USER, 42L, 7002L, Duration.ofSeconds(10)))
        .thenReturn(Optional.of(Rotation.kept()));
    when(store.rotate(AccountType.USER, 42L, 7003L, Duration.ZERO)).thenReturn(Optional.empty());

    assertThat(adapter.rotate(AccountType.USER, 42L, 7001L, Duration.ofSeconds(10)))
        .contains(SessionRotation.rotated(token.value()));
    assertThat(adapter.rotate(AccountType.USER, 42L, 7002L, Duration.ofSeconds(10)))
        .contains(SessionRotation.kept());
    assertThat(adapter.rotate(AccountType.USER, 42L, 7003L, Duration.ZERO)).isEmpty();
  }

  @Test
  @DisplayName("读再认证时间：取会话的 authenticatedAt；会话不在为空")
  void should_find_authenticated_at_from_stored_session() {
    when(store.find(AccountType.USER, 42L, 7001L))
        .thenReturn(
            Optional.of(
                StoredSession.builder()
                    .userId(42L)
                    .sessionId(7001L)
                    .accountType(AccountType.USER)
                    .clientType(ClientType.WEB)
                    .createdAt(NOW)
                    .lastActiveAt(NOW)
                    .expiresAt(NOW.plus(Duration.ofDays(180)))
                    .authenticatedAt(NOW.plusSeconds(5))
                    .build()));
    when(store.find(AccountType.USER, 42L, 7002L)).thenReturn(Optional.empty());

    assertThat(adapter.findAuthenticatedAt(AccountType.USER, 42L, 7001L))
        .contains(NOW.plusSeconds(5));
    assertThat(adapter.findAuthenticatedAt(AccountType.USER, 42L, 7002L)).isEmpty();
  }

  @Test
  @DisplayName("列出活会话：只取会话 ID、创建时间、最后活跃")
  void should_list_active_sessions() {
    when(store.list(AccountType.USER, 42L))
        .thenReturn(
            List.of(
                StoredSession.builder()
                    .userId(42L)
                    .sessionId(7001L)
                    .accountType(AccountType.USER)
                    .clientType(ClientType.WEB)
                    .createdAt(NOW)
                    .lastActiveAt(NOW.plusSeconds(120))
                    .expiresAt(NOW.plus(Duration.ofDays(180)))
                    .authenticatedAt(NOW)
                    .build()));

    assertThat(adapter.listActive(AccountType.USER, 42L))
        .containsExactly(ActiveUserSession.of(7001L, NOW, NOW.plusSeconds(120)));
  }

  @Test
  @DisplayName("按令牌查会话：格式不对直接为空，不碰存储")
  void should_skip_store_when_token_is_malformed() {
    assertThat(adapter.findAndTouch("not-a-session-token")).isEmpty();

    verifyNoInteractions(store);
  }

  @Test
  @DisplayName("按令牌查会话：存储里没有为空")
  void should_return_empty_when_session_is_absent() {
    SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());
    when(store.findAndTouch(token)).thenReturn(Optional.empty());

    assertThat(adapter.findAndTouch(token.value())).isEmpty();
  }

  @Test
  @DisplayName("按令牌查会话：查到转成当前用户")
  void should_return_current_user_when_session_is_found() {
    SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());
    when(store.findAndTouch(token))
        .thenReturn(
            Optional.of(
                StoredSession.builder()
                    .userId(42L)
                    .sessionId(7001L)
                    .accountType(AccountType.USER)
                    .clientType(ClientType.WEB)
                    .createdAt(NOW)
                    .lastActiveAt(NOW)
                    .expiresAt(NOW.plus(Duration.ofDays(180)))
                    .authenticatedAt(NOW)
                    .build()));

    assertThat(adapter.findAndTouch(token.value()))
        .contains(CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB));
  }
}
