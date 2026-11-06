package dev.patra.identity.infra.account.verification;

/// 发给第三方之前对令牌做的检查：挡掉明显不是令牌的输入，少一次外部调用。
final class VerificationTokenRules {

  /// 只有静态方法。
  private VerificationTokenRules() {}

  /// 判断令牌能不能发给第三方：非空、不超长、不含空白和控制字符。
  ///
  /// @param token 令牌
  /// @param maxLength 长度上限
  /// @return 能发时为 `true`
  static boolean isSendable(String token, int maxLength) {
    if (token == null || token.isEmpty() || token.length() > maxLength) {
      return false;
    }
    return token.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c));
  }
}
