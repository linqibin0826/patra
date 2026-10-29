package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 账号正在注销冷静期，除取消注销与登出外的写操作被拒，返回 409。
///
/// 除了 `CONFLICT` 还带 {@link IdentityErrorTrait#PENDING_DELETION}，响应的 `traits[]` 里能看到它，
/// 门户据此进「取消注销」页。
public final class UserPendingDeletionException extends DomainException {

  /// 创建异常。
  public UserPendingDeletionException() {
    super("账号正在注销中，取消注销后再操作", StandardErrorTrait.CONFLICT, IdentityErrorTrait.PENDING_DELETION);
  }
}
