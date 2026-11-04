package dev.patra.identity.domain.account.model.vo;

/// 校验验证码的结果。
public sealed interface CodeCheck {

  /// 对了，验证码已用掉。
  ///
  /// @param username 发码时绑定的用户名
  /// @param email 发码时绑定的邮箱
  record Matched(String username, String email) implements CodeCheck {}

  /// 不对，还能再试。
  record Mismatch() implements CodeCheck {}

  /// 没有可用的验证码：没发过、过期、用过，或这次错满次数刚刚作废。
  record NoActiveCode() implements CodeCheck {}
}
