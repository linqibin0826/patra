package dev.patra.identity.domain.session.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 在设备列表里踢的是本机（409）：本机走退出登录（PAP-99 设计第 11.2 节）。
public final class CurrentSessionRevocationException extends DomainException {

  /// 创建异常。
  public CurrentSessionRevocationException() {
    super("不能踢掉本机，请用退出登录", StandardErrorTrait.CONFLICT, IdentityErrorTrait.CURRENT_SESSION);
  }
}
