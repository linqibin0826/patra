package dev.patra.identity.app.account.usecase.login;

import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import java.util.Objects;

/// 登录或注册成功的结果：会话令牌，加上和「当前用户」接口同一个结构的账号信息。
///
/// @param sessionToken 会话令牌原文，只出现在响应里
/// @param account 账号信息
public record AuthenticatedUser(String sessionToken, UserAccountReadModel account) {

  /// 校验令牌非空、账号非空。
  public AuthenticatedUser {
    if (sessionToken == null || sessionToken.isBlank()) {
      throw new IllegalArgumentException("sessionToken 不能为空");
    }
    Objects.requireNonNull(account, "account 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param sessionToken 会话令牌
  /// @param account 账号信息
  /// @return 结果
  public static AuthenticatedUser of(String sessionToken, UserAccountReadModel account) {
    return new AuthenticatedUser(sessionToken, account);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AuthenticatedUser[sessionToken=***, account=" + account + "]";
  }
}
