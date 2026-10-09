package dev.linqibin.patra.identity.app.usecase.user.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.AuthenticationRequiredException;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import dev.linqibin.patra.identity.domain.port.read.UserReadPort;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UserQueryService 单元测试。
@DisplayName("UserQueryService 单元测试")
class UserQueryServiceTest {

  private static final CurrentUser USER =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final UserQueryService service = new UserQueryService(currentUserPort, userReadPort);

  @Test
  @DisplayName("有当前用户且账号正常：返回 ID、邮箱、状态")
  void should_return_current_account() {
    when(currentUserPort.require()).thenReturn(USER);
    when(userReadPort.findAccount(42L))
        .thenReturn(
            Optional.of(UserAccountReadModel.of(42L, "chen.yu@example.com", UserStatus.ACTIVE)));

    UserAccountReadModel account = service.currentAccount();

    assertThat(account.userId()).isEqualTo(42L);
    assertThat(account.email()).isEqualTo("chen.yu@example.com");
  }

  @Test
  @DisplayName("没有当前用户：401，不查库")
  void should_require_authentication() {
    when(currentUserPort.require()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(userReadPort);
  }

  @Test
  @DisplayName("会话指向的用户不存在：401")
  void should_reject_missing_user() {
    when(currentUserPort.require()).thenReturn(USER);
    when(userReadPort.findAccount(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  @DisplayName("用户已封禁但断言还没过期：401")
  void should_reject_banned_user() {
    when(currentUserPort.require()).thenReturn(USER);
    when(userReadPort.findAccount(42L))
        .thenReturn(
            Optional.of(UserAccountReadModel.of(42L, "chen.yu@example.com", UserStatus.BANNED)));

    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
  }
}
