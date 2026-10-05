package dev.linqibin.patra.starter.security.header;

import dev.linqibin.patra.common.security.CurrentUser;

/// 解析身份头的三种结果。
public sealed interface IdentityParseResult {

  /// 四个身份头齐全且合法。
  ///
  /// @param user 解析出的当前用户
  /// @return 结果
  static IdentityParseResult identified(CurrentUser user) {
    return new Identified(user);
  }

  /// 四个身份头都没有。
  ///
  /// @return 结果
  static IdentityParseResult absent() {
    return new Absent();
  }

  /// 身份头只有一部分、出现多次或格式不合法。
  ///
  /// @param reason 原因，只含头名，不含头的值
  /// @return 结果
  static IdentityParseResult malformed(String reason) {
    return new Malformed(reason);
  }

  /// 四个身份头齐全且合法。
  ///
  /// @param user 解析出的当前用户
  record Identified(CurrentUser user) implements IdentityParseResult {}

  /// 四个身份头都没有。
  record Absent() implements IdentityParseResult {}

  /// 身份头只有一部分、出现多次或格式不合法。
  ///
  /// @param reason 原因，只含头名，不含头的值
  record Malformed(String reason) implements IdentityParseResult {}
}
