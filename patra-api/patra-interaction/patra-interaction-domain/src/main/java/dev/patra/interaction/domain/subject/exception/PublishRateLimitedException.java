package dev.patra.interaction.domain.subject.exception;

import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.retry.HasRetryAfter;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.Objects;
import lombok.Getter;

/// 发表太频繁（429）：离上一条太近（`TOO_FREQUENT`）或今天的条数到了上限（`DAILY_LIMIT_REACHED`）。
/// 带上还要等多久，统一错误格式据此输出 `Retry-After` 与 `retryAfterSeconds`。
@Getter
public final class PublishRateLimitedException extends DomainException implements HasRetryAfter {

  private final PublishKind kind;
  private final Duration retryAfter;

  /// 创建异常。
  ///
  /// @param message 默认文案
  /// @param reason 原因码
  /// @param kind 发表种类
  /// @param retryAfter 还要等多久
  private PublishRateLimitedException(
      String message, SubjectErrorTrait reason, PublishKind kind, Duration retryAfter) {
    super(message, StandardErrorTrait.QUOTA_EXCEEDED, reason);
    this.kind = kind;
    this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter 不能为 null");
  }

  /// 离上一条太近。
  ///
  /// @param kind 发表种类
  /// @param retryAfter 还要等多久
  /// @return 异常
  public static PublishRateLimitedException tooFrequent(PublishKind kind, Duration retryAfter) {
    return new PublishRateLimitedException(
        "发得太快了，请隔 " + kind.minInterval().toSeconds() + " 秒再发",
        SubjectErrorTrait.TOO_FREQUENT,
        kind,
        retryAfter);
  }

  /// 今天的条数到了上限。
  ///
  /// @param kind 发表种类
  /// @param retryAfter 到北京时间明天 0 点还有多久
  /// @return 异常
  public static PublishRateLimitedException dailyLimitReached(
      PublishKind kind, Duration retryAfter) {
    return new PublishRateLimitedException(
        "今天已经" + kind.dailyLimitPhrase() + "，明天再来吧",
        SubjectErrorTrait.DAILY_LIMIT_REACHED,
        kind,
        retryAfter);
  }
}
