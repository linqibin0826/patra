package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.session.exception.IdentityErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 绑定回调时的用户或会话与发起时不一致，返回 403。防绑定 CSRF：别人的回调链接到不了你的账号。
public final class LinkSessionMismatchException extends DomainException {

  /// 创建异常。
  public LinkSessionMismatchException() {
    super(
        "绑定没有完成，请回到设置页重新发起",
        StandardErrorTrait.FORBIDDEN,
        IdentityErrorTrait.LINK_SESSION_MISMATCH);
  }
}
