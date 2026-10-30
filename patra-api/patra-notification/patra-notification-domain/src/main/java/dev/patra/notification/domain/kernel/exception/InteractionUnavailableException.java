package dev.patra.notification.domain.kernel.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// interaction 不可用（503 语义）：对象视图端口取不到数据。站内列表会降级为不带对象信息，不往外传播。
public final class InteractionUnavailableException extends DomainException {

  /// 创建异常。
  ///
  /// @param cause 下游失败
  public InteractionUnavailableException(Throwable cause) {
    super("讨论信息暂时无法获取，请稍后再试", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
