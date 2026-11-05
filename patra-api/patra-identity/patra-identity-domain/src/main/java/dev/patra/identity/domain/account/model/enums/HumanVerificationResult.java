package dev.patra.identity.domain.account.model.enums;

/// 一次人机验证的结果。
public enum HumanVerificationResult {
  /// 通过。
  PASSED,
  /// 令牌没通过：无效、过期、已用过，或和这次操作对不上。
  REJECTED,
  /// 验证不了：第三方超时、报错或配置缺失。按拒绝处理，不放行。
  UNAVAILABLE
}
