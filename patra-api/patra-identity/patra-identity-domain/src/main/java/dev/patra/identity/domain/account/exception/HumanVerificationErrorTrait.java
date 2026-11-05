package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// 人机验证的原因码：随一个标准特征一起放进异常。错误引擎映射状态码时跳过它，响应的 `traits[]` 里带上它。
public enum HumanVerificationErrorTrait implements ErrorTrait {
  /// 验证不了（503）：第三方不可用或配置缺失，门户据此提示这是验证问题。
  VERIFICATION_UNAVAILABLE
}
