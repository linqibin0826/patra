package dev.patra.identity.app.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final ReauthenticationGuard guard = mock(ReauthenticationGuard.class);
  private final SessionTerminator terminator = mock(SessionTerminator.class);
  private final RevokeOtherSessionsHandler handler =
      new RevokeOtherSessionsHandler(
          currentUserPort, guard, terminator, Clock.fixed(NOW, ZoneOffset.UTC));

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
}
