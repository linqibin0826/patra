package dev.linqibin.patra.identity.domain.exception;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.retry.HasRetryAfter;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.Objects;

/// 登录被暂时限制，返回 429，带上还要等多久。
public final class LoginTemporarilyLockedException extends DomainException
    implements HasRetryAfter {

  private final Duration retryAfter;

  /// 创建异常。
  ///
  /// @param retryAfter 剩余等待时间
  public LoginTemporarilyLockedException(Duration retryAfter) {
    super("尝试次数过多，请稍后再试", StandardErrorTrait.QUOTA_EXCEEDED);
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
