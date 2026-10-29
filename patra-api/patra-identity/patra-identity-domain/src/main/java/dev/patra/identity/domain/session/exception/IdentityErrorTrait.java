package dev.patra.identity.domain.session.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// identity 自己的语义特征：随一个标准特征一起放进异常。错误引擎映射状态码时跳过它，
/// 响应的 `traits[]` 里带上它，客户端把它当「原因码」读。一个异常只放一个标准特征，
/// 状态码才不取决于集合的遍历顺序。
public enum IdentityErrorTrait implements ErrorTrait {
  /// 账号正在注销冷静期，写操作被拒（409）。
  PENDING_DELETION,

  /// 距上次改名不足冷却期，改名被拒（409）。
  USERNAME_CHANGE_COOLDOWN,

  /// 邮箱已经验证过了，重发验证邮件被拒（409）。
  EMAIL_ALREADY_VERIFIED,

  /// 最近一次输密码已超过再认证窗口，敏感操作被拒（403）；门户弹密码框后重试。
  REAUTH_REQUIRED,

  /// 已有一个待确认的改邮箱申请（409）。
  EMAIL_CHANGE_PENDING,

  /// 再认证或改密码时当前密码不正确（401）；和「会话失效」的 401 区分开，门户不把它当登出。
  PASSWORD_MISMATCH,

  /// 在登录设备列表里踢的是本机（409）；本机走退出登录（PAP-99）。
  CURRENT_SESSION
}
