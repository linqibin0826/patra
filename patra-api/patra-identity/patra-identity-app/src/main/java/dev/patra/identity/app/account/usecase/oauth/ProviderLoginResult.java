package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;

/// 第三方登录回调的结果：老用户已发会话，或新用户要补填。
public sealed interface ProviderLoginResult {

  /// 找到了账号，已发会话。
  ///
  /// @param user 会话令牌和账号信息
  record Authenticated(AuthenticatedUser user) implements ProviderLoginResult {}

  /// 没有账号，已建待补填记录。
  ///
  /// @param ticket 待补填凭证原文，只给发起的浏览器
  /// @param pending 待补填记录
  record RegistrationRequired(String ticket, PendingRegistration pending)
      implements ProviderLoginResult {

    /// 不输出凭证。
    ///
    /// @return 描述
    @Override
    public String toString() {
      return "RegistrationRequired[ticket=***, pending=" + pending + "]";
    }
  }
}
