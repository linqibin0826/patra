package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 当前用户的登录方式。状态判断同 `UserQueryService.currentAccount()`。
@Component
@RequiredArgsConstructor
public class LoginMethodQueryService {

  private final UserQueryService userQueryService;
  private final ExternalIdentityRepository identities;

  /// 当前用户的登录方式。
  ///
  /// @return 有没有密码和绑定的第三方账号
  public LoginMethodsView current() {
    UserAccountReadModel account = userQueryService.currentAccount();
    return LoginMethodsView.of(account.hasPassword(), identities.findAllByUserId(account.userId()));
  }
}
