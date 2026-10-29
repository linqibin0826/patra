package dev.patra.identity.domain.account.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.retry.HasRetryAfter;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.Objects;

/// 改邮箱的申请超过每天的上限，返回 429，带上还要等多久。
public final class EmailChangeLimitedException extends DomainException implements HasRetryAfter {

  private final Duration retryAfter;

  /// 创建异常。
  ///
  /// @param retryAfter 剩余等待时间，取各维度里最长的
  public EmailChangeLimitedException(Duration retryAfter) {
    super("今天更改邮箱的次数已用完，请明天再试", StandardErrorTrait.QUOTA_EXCEEDED);
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
