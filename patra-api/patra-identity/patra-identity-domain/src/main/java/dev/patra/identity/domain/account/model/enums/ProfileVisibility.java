package dev.patra.identity.domain.account.model.enums;

/// 主页可见性：用户希望主页被谁看到。取值与 `PublicAudience` 同名，账号正常且邮箱已验证时两者相等。
public enum ProfileVisibility {
  /// 谁都能看。
  PUBLIC,
  /// 只有登录用户能看。
  SIGNED_IN,
  /// 只有本人能看。
  SELF
}
