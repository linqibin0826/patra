package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 依赖暂时不可用（Redis 连不上、密码哈希排队超时），返回 503。
public final class TemporarilyUnavailableException extends DomainException {

  /// 创建异常。
  public TemporarilyUnavailableException() {
    super("服务暂时不可用", StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 创建异常并保留原因。
  ///
  /// @param cause 底层异常
  public TemporarilyUnavailableException(Throwable cause) {
    super("服务暂时不可用", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
