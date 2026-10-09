package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
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
            .build();

    assertThat(session.toCurrentUser())
        .isEqualTo(CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB));
    assertThat(session.device()).isEmpty();
  }
}
