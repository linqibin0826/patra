package dev.patra.notification.domain.kernel.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// identity 不可用（503）：收件信息端口取不到数据。调用方默认直接传播。
public final class IdentityUnavailableException extends DomainException {

  /// 创建异常。
  ///
  /// @param cause 下游失败
  public IdentityUnavailableException(Throwable cause) {
    super("用户信息暂时无法获取，请稍后再试", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
