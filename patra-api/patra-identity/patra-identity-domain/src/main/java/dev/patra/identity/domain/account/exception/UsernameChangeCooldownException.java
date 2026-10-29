package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 距上次改名还在冷却期内，返回 409，`traits` 里带 `USERNAME_CHANGE_COOLDOWN`。
///
/// 不用 429：冷却按天计，而 HTTP 客户端普遍把 429 当瞬时错误、按 `Retry-After` 自动等待重试。
/// 下次可以改名的时间由 `/auth/me` 的 `nextUsernameChangeAt` 给出。
public final class UsernameChangeCooldownException extends DomainException {

  /// 创建异常。
  public UsernameChangeCooldownException() {
    super(
        "用户名修改过于频繁，请稍后再试",
        StandardErrorTrait.CONFLICT,
        IdentityErrorTrait.USERNAME_CHANGE_COOLDOWN);
  }
}
