package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.cqrs.Command;
import lombok.Builder;

/// 绑定回调：平台确认了身份，核对发起人后绑定。
///
/// @param account 平台确认过的账号
/// @param initiatorUserId 发起绑定的用户 ID
/// @param initiatorSessionId 发起绑定的会话 ID
/// @param current 回调请求的当前用户，没带会话时为 `null`
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`
@Builder
public record LinkProviderAccountCommand(
    ProviderAccount account,
    long initiatorUserId,
    long initiatorSessionId,
    CurrentUser current,
    String clientIp,
    String userAgent)
    implements Command<ProviderLinked> {

  /// 不输出 UA 原文。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LinkProviderAccountCommand[provider="
        + account.provider()
        + ", initiatorUserId="
        + initiatorUserId
        + ", clientIp="
        + clientIp
        + "]";
  }
}
