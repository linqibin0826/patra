package dev.patra.identity.domain.session.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 保存用户时撞上乐观锁：封禁、解封并发时后到的一方收到 409，重试即可。
public final class UserModifiedConcurrentlyException extends DomainException {

  /// 创建异常。
  public UserModifiedConcurrentlyException() {
    super("用户正被其他操作修改，请重试", StandardErrorTrait.CONFLICT);
  }
}
