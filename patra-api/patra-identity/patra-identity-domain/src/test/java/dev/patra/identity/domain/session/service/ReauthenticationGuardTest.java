package dev.patra.identity.domain.session.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.identity.domain.session.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.session.policy.ReauthenticationPolicy;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ReauthenticationGuard 单元测试：窗口内通过、超窗 403、会话不在 401。
@DisplayName("ReauthenticationGuard 单元测试")
class ReauthenticationGuardTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);

  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final ReauthenticationGuard guard =
      new ReauthenticationGuard(
          sessions,
          ReauthenticationPolicy.of(Duration.ofMinutes(10), Duration.ofSeconds(10)),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("窗口内通过")
  void should_pass_within_window() {
    when(sessions.findAuthenticatedAt(AccountType.USER, 42L, 7001L))
        .thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(9))));

    assertThatCode(() -> guard.requireRecent(CURRENT)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("超窗 403：FORBIDDEN + REAUTH_REQUIRED")
  void should_require_reauth_after_window() {
    when(sessions.findAuthenticatedAt(AccountType.USER, 42L, 7001L))
        .thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(10))));

    assertThatThrownBy(() -> guard.requireRecent(CURRENT))
        .isInstanceOf(ReauthenticationRequiredException.class)
        .extracting("errorTraits", InstanceOfAssertFactories.COLLECTION)
        .containsExactlyInAnyOrder(
            StandardErrorTrait.FORBIDDEN, IdentityErrorTrait.REAUTH_REQUIRED);
  }

  @Test
  @DisplayName("再认证时间远早于窗口（EPOCH）视作超窗")
  void should_treat_epoch_as_never_authenticated() {
    when(sessions.findAuthenticatedAt(AccountType.USER, 42L, 7001L))
        .thenReturn(Optional.of(Instant.EPOCH));

    assertThatThrownBy(() -> guard.requireRecent(CURRENT))
        .isInstanceOf(ReauthenticationRequiredException.class);
  }

  @Test
  @DisplayName("会话已不在：401")
  void should_require_login_when_session_is_gone() {
    when(sessions.findAuthenticatedAt(AccountType.USER, 42L, 7001L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> guard.requireRecent(CURRENT))
        .isInstanceOf(AuthenticationRequiredException.class);
  }
}
