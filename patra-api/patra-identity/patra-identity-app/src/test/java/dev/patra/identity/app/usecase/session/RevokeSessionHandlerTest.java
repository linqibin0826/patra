package dev.patra.identity.app.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.exception.CurrentSessionRevocationException;
import dev.patra.identity.domain.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.model.enums.LoginEndReason;
import dev.patra.identity.domain.service.ReauthenticationGuard;
import dev.patra.identity.domain.service.SessionTerminator;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// RevokeSessionHandler 单元测试：踢单个设备（PAP-99 设计第 11.2 节）。
@DisplayName("RevokeSessionHandler 单元测试")
class RevokeSessionHandlerTest {

  static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  static final CurrentUser CURRENT = CurrentUser.of(42L, 7002L, AccountType.USER, ClientType.WEB);

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final ReauthenticationGuard guard = mock(ReauthenticationGuard.class);
  private final SessionTerminator terminator = mock(SessionTerminator.class);
  private final RevokeSessionHandler handler =
      new RevokeSessionHandler(
          currentUserPort, guard, terminator, Clock.fixed(NOW, ZoneOffset.UTC));

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
  @DisplayName("会话已经不在：返回 0，不报错")
  void should_return_zero_when_session_already_gone() {
    when(terminator.terminate(42L, 7001L, LoginEndReason.REVOKED, NOW)).thenReturn(false);

    assertThat(handler.handle(RevokeSessionCommand.of(7001L)).revokedSessions()).isZero();
  }
}
