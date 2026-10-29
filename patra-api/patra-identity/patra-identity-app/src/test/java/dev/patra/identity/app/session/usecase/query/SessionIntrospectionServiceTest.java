package dev.patra.identity.app.session.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SessionIntrospectionService 单元测试：按令牌查会话，结果原样来自会话存储（PAP-106）。
@DisplayName("SessionIntrospectionService 单元测试")
class SessionIntrospectionServiceTest {

  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final SessionIntrospectionService service = new SessionIntrospectionService(sessions);

  @Test
  @DisplayName("查到会话返回当前用户，查不到为空")
  void should_return_current_user_from_session_store() {
    CurrentUser user = CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
    when(sessions.findAndTouch("patra_user_live")).thenReturn(Optional.of(user));
    when(sessions.findAndTouch("patra_user_gone")).thenReturn(Optional.empty());

    assertThat(service.introspect("patra_user_live")).contains(user);
    assertThat(service.introspect("patra_user_gone")).isEmpty();
  }
}
