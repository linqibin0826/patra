package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 头像不存在：用户名、尺寸、版本对不上，或对象已不在，一律 404，不区分原因。
public final class AvatarNotFoundException extends DomainException {

  /// 创建异常。
  public AvatarNotFoundException() {
    super("头像不存在", StandardErrorTrait.NOT_FOUND);
  }
}
