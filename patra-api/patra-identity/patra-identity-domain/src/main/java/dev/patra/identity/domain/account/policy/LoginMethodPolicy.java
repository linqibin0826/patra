package dev.patra.identity.domain.account.policy;

import dev.patra.identity.domain.account.exception.LastLoginMethodException;

/// 登录方式的规则。
public final class LoginMethodPolicy {

  /// 只有静态方法。
  private LoginMethodPolicy() {}

  /// 解绑一个第三方账号之后还要进得来：有密码，或者还绑着别的平台。
  ///
  /// @param hasPassword 有没有密码
  /// @param linkedCount 现在绑了几家，含要解绑的这家
  /// @throws LastLoginMethodException 没有密码且只剩这一种
  public static void requireCanUnlink(boolean hasPassword, long linkedCount) {
    if (!hasPassword && linkedCount <= 1) {
      throw new LastLoginMethodException();
    }
  }
}
