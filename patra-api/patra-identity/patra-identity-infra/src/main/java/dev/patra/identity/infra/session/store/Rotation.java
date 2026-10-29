package dev.patra.identity.infra.session.store;

import java.util.Optional;

/// 换令牌的结果：换了就带新令牌；`auth_at` 还在宽限期内没换，则没有令牌、原令牌继续有效。
///
/// @param token 新令牌；没换时为 `null`
public record Rotation(SessionToken token) {
  /// 换了令牌。
  ///
  /// @param token 新令牌
  /// @return 结果
  public static Rotation rotated(SessionToken token) {
    if (token == null) {
      throw new IllegalArgumentException("token 不能为 null");
    }
    return new Rotation(token);
  }

  /// 没换令牌。
  ///
  /// @return 结果
  public static Rotation kept() {
    return new Rotation(null);
  }

  /// 是否换了令牌。
  ///
  /// @return 换了为 `true`
  public boolean rotated() {
    return token != null;
  }

  /// 新令牌。
  ///
  /// @return 没换时为空
  public Optional<SessionToken> newToken() {
    return Optional.ofNullable(token);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "Rotation[rotated=" + rotated() + "]";
  }
}
