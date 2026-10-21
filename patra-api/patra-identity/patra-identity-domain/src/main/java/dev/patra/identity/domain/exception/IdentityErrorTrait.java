package dev.patra.identity.domain.exception;

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
  EMAIL_ALREADY_VERIFIED
}
