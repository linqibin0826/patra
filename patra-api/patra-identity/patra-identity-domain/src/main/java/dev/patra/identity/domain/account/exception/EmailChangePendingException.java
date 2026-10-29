package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 已有一个待确认的改邮箱申请，返回 409。除了 `CONFLICT` 还带
/// {@link IdentityErrorTrait#EMAIL_CHANGE_PENDING}；待确认的地址不进文案，门户用 `GET /me/email/change` 取。
public final class EmailChangePendingException extends DomainException {

  /// 创建异常。
  public EmailChangePendingException() {
    super(
        "已有一个待确认的邮箱更改，请先取消或等它过期",
        StandardErrorTrait.CONFLICT,
        IdentityErrorTrait.EMAIL_CHANGE_PENDING);
  }
}
