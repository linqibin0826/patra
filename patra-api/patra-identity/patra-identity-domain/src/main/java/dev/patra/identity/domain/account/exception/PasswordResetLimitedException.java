package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.retry.HasRetryAfter;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.Objects;

/// 找回密码的申请或提交过于频繁（冷却或某个窗口已满），返回 429，带上还要等多久。
public final class PasswordResetLimitedException extends DomainException implements HasRetryAfter {

  private final Duration retryAfter;

  /// 创建异常。
  ///
  /// @param retryAfter 剩余等待时间，取各维度里最长的
  public PasswordResetLimitedException(Duration retryAfter) {
    super("操作过于频繁，请稍后再试", StandardErrorTrait.QUOTA_EXCEEDED);
    this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter 不能为 null");
  }

  /// 返回剩余等待时间。
  ///
  /// @return 剩余等待时间
  @Override
  public Duration getRetryAfter() {
    return retryAfter;
  }
}
