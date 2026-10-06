package dev.linqibin.patra.common.security;

import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// 账号类型。
///
/// 本版只有前台用户。后台账号（`STAFF`）在做后台时加入。
@Getter
@RequiredArgsConstructor
public enum AccountType {
  /// 前台用户：自己注册的外部用户，门户 Web、App、小程序共用这一类账号。
  USER("user");

  /// 写进请求头和会话里的字符串形式。
  private final String code;

  /// 按字符串查找账号类型。
  ///
  /// @param code 字符串形式，区分大小写
  /// @return 匹配的账号类型；不认识或为 `null` 时返回空
  public static Optional<AccountType> fromCode(String code) {
    for (AccountType type : values()) {
      if (type.code.equals(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
