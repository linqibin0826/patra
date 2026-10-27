package dev.patra.interaction.app.post;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.Optional;

/// 测试用的当前用户端口。
public final class TestUsers {

  /// 只有静态方法。
  private TestUsers() {}

  /// 已登录的当前用户。
  ///
  /// @param userId 用户 ID
  /// @return 端口
  public static CurrentUserPort signedIn(long userId) {
    CurrentUserPort port = mock(CurrentUserPort.class);
    CurrentUser user = CurrentUser.of(userId, 1L, AccountType.USER, ClientType.WEB);
    when(port.current()).thenReturn(Optional.of(user));
    when(port.require()).thenReturn(user);
    return port;
  }

  /// 匿名。
  ///
  /// @return 端口
  public static CurrentUserPort anonymous() {
    CurrentUserPort port = mock(CurrentUserPort.class);
    when(port.current()).thenReturn(Optional.empty());
    return port;
  }
}
