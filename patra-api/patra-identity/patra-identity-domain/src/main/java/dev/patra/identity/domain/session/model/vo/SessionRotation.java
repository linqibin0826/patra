package dev.patra.identity.domain.session.model.vo;

import java.util.Optional;

/// 换令牌的结果：换了就带新令牌原文；`auth_at` 还在宽限期内没换，则没有新令牌、原令牌继续有效。
///
/// @param token 新令牌原文；没换时为 `null`
public record SessionRotation(String token) {
  /// 换了令牌。
  ///
  /// @param token 新令牌原文
  /// @return 结果
  public static SessionRotation rotated(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token 不能为空");
    }
    return new SessionRotation(token);
  }

  /// 没换令牌。
  ///
  /// @return 结果
  public static SessionRotation kept() {
    return new SessionRotation(null);
  }

  /// 是否换了令牌。
  ///
  /// @return 换了为 `true`
  public boolean rotated() {
    return token != null;
  }

  /// 新令牌原文。
  ///
  /// @return 没换时为空
  public Optional<String> newToken() {
    return Optional.ofNullable(token);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "SessionRotation[rotated=" + rotated() + "]";
  }
}
