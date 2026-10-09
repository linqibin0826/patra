package dev.linqibin.patra.identity.app.usecase.user.query;

import dev.linqibin.patra.common.security.AuthenticationRequiredException;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import dev.linqibin.patra.identity.domain.port.read.UserReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 「当前用户」的查询。只读，不经 CommandBus，没有事务。
///
/// 没有当前用户、会话指向的用户不存在、用户已封禁，三种情况都抛
/// {@link AuthenticationRequiredException}（401）：对客户端来说都是「这个会话不再有效」。
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryService {

  private final CurrentUserPort currentUserPort;
  private final UserReadPort userReadPort;

  /// 当前登录用户的账号信息。
  ///
  /// @return 账号信息
  /// @throws AuthenticationRequiredException 没有当前用户、用户不存在或已封禁
  public UserAccountReadModel currentAccount() {
    CurrentUser current = currentUserPort.require();
    UserAccountReadModel account =
        userReadPort
            .findAccount(current.userId())
            .orElseThrow(
                () -> {
                  log.warn(
                      "会话指向的用户不存在: userId={}, sessionId={}", current.userId(), current.sessionId());
                  return new AuthenticationRequiredException();
                });
    if (account.status() == UserStatus.BANNED) {
      log.warn("已封禁用户的断言仍在有效期内: userId={}, sessionId={}", current.userId(), current.sessionId());
      throw new AuthenticationRequiredException();
    }
    return account;
  }
}
