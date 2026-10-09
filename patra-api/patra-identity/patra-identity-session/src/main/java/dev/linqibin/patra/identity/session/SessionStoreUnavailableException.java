package dev.linqibin.patra.identity.session;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 会话存储暂时不可用（Redis 连不上、超时、正在加载等），返回 503。
public final class SessionStoreUnavailableException extends DomainException {

  /// 创建异常并保留原因。
  ///
  /// @param cause 底层异常
  public SessionStoreUnavailableException(Throwable cause) {
    super("服务暂时不可用", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
