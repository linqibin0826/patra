package dev.patra.identity.domain.session.model.vo;

import java.time.Instant;
import java.util.Optional;

/// 一条还活着的会话最近一次用密码证明身份的情况，再认证守卫用。
///
/// 第三方登录建的会话没有密码证明。这个值只在会话存在时才有：「会话不在」是外层的空，
/// 「会话在但没输过密码」是这里的空，两者分别是 401 和 403。
///
/// @param verifiedAtOrNull 最近一次输密码的时间，没输过为 `null`
public record SessionPasswordProof(Instant verifiedAtOrNull) {

  /// 输过密码。
  ///
  /// @param verifiedAt 时间，可以为 `null`（等于没输过）
  /// @return 证明
  public static SessionPasswordProof of(Instant verifiedAt) {
    return new SessionPasswordProof(verifiedAt);
  }

  /// 没输过密码。
  ///
  /// @return 证明
  public static SessionPasswordProof none() {
    return new SessionPasswordProof(null);
  }

  /// 最近一次输密码的时间。
  ///
  /// @return 时间；没输过时为空
  public Optional<Instant> verifiedAt() {
    return Optional.ofNullable(verifiedAtOrNull);
  }
}
