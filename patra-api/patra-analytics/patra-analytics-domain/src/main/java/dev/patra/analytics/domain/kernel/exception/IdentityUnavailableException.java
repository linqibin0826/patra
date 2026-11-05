package dev.patra.analytics.domain.kernel.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// identity 不可用：注销清理取不到账号状态，跳过这一批、次日重跑。
public final class IdentityUnavailableException extends DomainException {

  /// 创建异常。
  ///
  /// @param cause 下游失败
  public IdentityUnavailableException(Throwable cause) {
    super("用户信息暂时无法获取，请稍后再试", cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
