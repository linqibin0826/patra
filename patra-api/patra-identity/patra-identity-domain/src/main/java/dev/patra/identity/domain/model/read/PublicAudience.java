package dev.patra.identity.domain.model.read;

/// 公开身份卡能被谁看到。本 Issue 只有两档；PAP-95 的「仅登录用户」加 `SIGNED_IN`。
public enum PublicAudience {
  /// 谁都能看：账号正常且邮箱已验证。
  PUBLIC,
  /// 只有本人能看：邮箱未验证，或账号在注销冷静期。
  SELF
}
