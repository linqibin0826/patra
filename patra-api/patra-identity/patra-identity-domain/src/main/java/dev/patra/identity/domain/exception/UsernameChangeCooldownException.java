package dev.patra.identity.domain.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.retry.HasRetryAfter;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.Objects;

/// 距上次改名还在冷却期内，返回 429，带上还要等多久。
public final class UsernameChangeCooldownException extends DomainException
    implements HasRetryAfter {

  private final Duration retryAfter;

  /// 创建异常。
  ///
  /// @param retryAfter 剩余的冷却时长
  public UsernameChangeCooldownException(Duration retryAfter) {
    super("用户名修改过于频繁，请稍后再试", StandardErrorTrait.QUOTA_EXCEEDED);
    this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter 不能为 null");
  }

  /// 返回剩余的冷却时长。
  ///
  /// @return 剩余时长
  @Override
  public Duration getRetryAfter() {
    return retryAfter;
  }
}
