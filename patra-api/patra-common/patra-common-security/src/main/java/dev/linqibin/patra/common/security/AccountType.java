package dev.linqibin.patra.common.security;

import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// 账号类型。
///
/// 本版只有门户用户。后台账号在做 admin 时加入。
@Getter
@RequiredArgsConstructor
public enum AccountType {
  /// 门户用户。
  PORTAL("portal");

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
