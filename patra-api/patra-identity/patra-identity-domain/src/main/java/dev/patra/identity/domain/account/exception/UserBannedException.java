package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 账号已被封禁，返回 403。只在邮箱和密码都对时抛出。
public final class UserBannedException extends DomainException {

  /// 创建异常。
  public UserBannedException() {
    super("该账号已被封禁", StandardErrorTrait.FORBIDDEN);
  }
}
