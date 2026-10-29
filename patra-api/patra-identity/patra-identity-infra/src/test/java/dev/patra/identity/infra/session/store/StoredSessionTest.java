package dev.patra.identity.infra.session.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// StoredSession 单元测试。
@DisplayName("StoredSession 单元测试")
class StoredSessionTest {

  @Test
  @DisplayName("转成当前用户：四个身份字段原样带过去")
  void should_convert_to_current_user() {
    Instant now = Instant.parse("2026-10-09T08:00:00Z");
    StoredSession session =
        StoredSession.builder()
            .userId(42L)
            .sessionId(7001L)
            .accountType(AccountType.USER)
            .clientType(ClientType.WEB)
            .createdAt(now)
            .lastActiveAt(now)
            .expiresAt(now.plusSeconds(3600))
            .authenticatedAt(now)
            .build();

    assertThat(session.toCurrentUser())
        .isEqualTo(CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB));
    assertThat(session.device()).isEmpty();
  }

  @Test
  @DisplayName("再认证时间不能为 null")
  void should_require_authenticated_at() {
    Instant now = Instant.parse("2026-10-09T08:00:00Z");
    assertThatThrownBy(
            () ->
                StoredSession.builder()
                    .userId(42L)
                    .sessionId(7001L)
                    .accountType(AccountType.USER)
                    .clientType(ClientType.WEB)
                    .createdAt(now)
                    .lastActiveAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .authenticatedAt(null)
                    .build())
        .isInstanceOf(NullPointerException.class);
  }
}
