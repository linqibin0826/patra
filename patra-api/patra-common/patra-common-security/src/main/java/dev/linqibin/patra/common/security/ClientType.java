package dev.linqibin.patra.common.security;

import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// 客户端类型。
///
/// 本版只有网页端。移动端、小程序端在接入时加入。
@Getter
@RequiredArgsConstructor
public enum ClientType {
  /// 网页端。
  WEB("web");

  /// 写进请求头和会话里的字符串形式。
  private final String code;

  /// 按字符串查找客户端类型。
  ///
  /// @param code 字符串形式，区分大小写
  /// @return 匹配的客户端类型；不认识或为 `null` 时返回空
  public static Optional<ClientType> fromCode(String code) {
    for (ClientType type : values()) {
      if (type.code.equals(code)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
