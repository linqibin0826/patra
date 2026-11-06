package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 验证不了，返回 503。按拒绝处理：第三方出问题时不放行。
public final class HumanVerificationUnavailableException extends DomainException {

  /// 创建异常。
  public HumanVerificationUnavailableException() {
    super(
        "人机验证暂时不可用",
        StandardErrorTrait.DEP_UNAVAILABLE,
        HumanVerificationErrorTrait.VERIFICATION_UNAVAILABLE);
  }
}
