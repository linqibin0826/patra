package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 用户不存在，返回 404。后台接口按 ID 查不到，或公开身份卡不存在、对请求者不可见，都是它：
/// 不可见的原因不区分，不给出「这个名字存在但你看不到」的信号。
public final class UserNotFoundException extends DomainException {

  /// 创建异常。
  public UserNotFoundException() {
    super("用户不存在", StandardErrorTrait.NOT_FOUND);
  }
}
