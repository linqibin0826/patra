package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.domain.account.exception.EmailNotVerifiedException;
import dev.patra.identity.domain.account.exception.ProviderAlreadyLinkedException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 发起绑定之前的检查。这些错误要以 JSON 回给门户（弹框），所以不放在框架的重定向过滤器里：它会把 resolver 的所有
/// 异常变成 500。
///
/// 顺序（错误从便宜到贵）：状态（409 / 401）→ 邮箱已验证（409，未验证的邮箱不能作为身份凭据，v0.12 规则）→
/// 这一家没绑过（409）→ 再认证（403）。
@Component
@RequiredArgsConstructor
public class PrepareProviderLinkHandler
    implements CommandHandler<PrepareProviderLinkCommand, CurrentUser> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final ExternalIdentityRepository identities;
  private final ReauthenticationGuard reauthenticationGuard;

  /// 检查。
  ///
  /// @param command 命令
  /// @return 发起人：用户和会话记进授权请求，回调时核对
  @Override
  public CurrentUser handle(PrepareProviderLinkCommand command) {
    CurrentUser current = currentUserPort.require();
    User user = users.findById(current.userId()).orElseThrow(AuthenticationRequiredException::new);
    user.requireActive();
    if (!user.isEmailVerified()) {
      throw new EmailNotVerifiedException();
    }
    if (identities.findByUserIdAndProvider(current.userId(), command.provider()).isPresent()) {
      throw new ProviderAlreadyLinkedException();
    }
    reauthenticationGuard.requireRecent(current);
    return current;
  }
}
