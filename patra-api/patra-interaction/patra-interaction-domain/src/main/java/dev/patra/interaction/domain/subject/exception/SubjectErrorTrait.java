package dev.patra.interaction.domain.subject.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// 底座的原因码：随一个标准特征一起放进异常。错误引擎映射状态码时跳过它，响应的 `traits[]` 里带上它，门户把它当
/// 原因码读（同 collection 的 `CollectionErrorTrait`）。
public enum SubjectErrorTrait implements ErrorTrait {
  /// 对象已登记但失效，或首次解析时来源说它已被软删（404）：门户显示「已删除」，而不是「不存在」。
  SUBJECT_UNAVAILABLE,

  /// 发表被拒：账号不是正常状态（封禁、注销冷静期、已注销，或 identity 不认识）（409）。与 collection 同名。
  ACCOUNT_RESTRICTED,

  /// 发表被拒：邮箱未验证（409）。与 collection 同名。
  EMAIL_UNVERIFIED,

  /// 发表被拒：离上一条太近（429）。
  TOO_FREQUENT,

  /// 发表被拒：今天的条数到了上限（429）。
  DAILY_LIMIT_REACHED
}
