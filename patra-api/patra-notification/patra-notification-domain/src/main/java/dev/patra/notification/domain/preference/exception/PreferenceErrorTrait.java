package dev.patra.notification.domain.preference.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// preference 子域的原因码（放进 ProblemDetail 的 `traits[]`）。
public enum PreferenceErrorTrait implements ErrorTrait {

  /// 退订链接无效：令牌缺失、格式不对、不存在，类别不对，或账号已注销（spec 5.4）。
  UNSUBSCRIBE_LINK_INVALID
}
