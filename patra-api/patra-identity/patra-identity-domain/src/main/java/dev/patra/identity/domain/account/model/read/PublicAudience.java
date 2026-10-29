package dev.patra.identity.domain.account.model.read;

/// 公开主页实际能被谁看到。账号正常且邮箱已验证时等于用户设的主页可见性；否则只有本人。
public enum PublicAudience {
  /// 谁都能看。
  PUBLIC,
  /// 只有登录用户能看（任何带有效会话的人，含邮箱未验证、冷静期中的账号）。
  SIGNED_IN,
  /// 只有本人能看：主页设为仅自己，或邮箱未验证、账号不在正常状态。
  SELF
}
