package dev.patra.analytics.domain.collect.policy;

import java.time.Duration;
import java.time.Instant;

/// 校正产生方时钟的偏差（Snowplow 的 `derived_tstamp`，埋点方案 §4.3）。
///
/// `derived_at = received_at − (sent_at − created_at)`：客户端时钟快了慢了都不影响「发生在收到之前多久」。
/// 结果早于 `received_at` 超过 24 小时、或晚于它超过 5 分钟时，说明时钟严重错乱，取 `received_at`。
public final class EventTimeCorrection {

  /// 校正结果最早能比收到时刻早这么久
  public static final Duration MAX_EARLY = Duration.ofHours(24);

  /// 校正结果最晚能比收到时刻晚这么久
  public static final Duration MAX_LATE = Duration.ofMinutes(5);

  private EventTimeCorrection() {}

  /// 算出校正后的发生时刻。
  ///
  /// @param createdAt 产生方时钟的发生时刻
  /// @param sentAt 产生方时钟的发送时刻
  /// @param receivedAt 采集端点收到的时刻
  /// @return 校正后的发生时刻
  public static Instant derive(Instant createdAt, Instant sentAt, Instant receivedAt) {
    Instant derived = receivedAt.minus(Duration.between(createdAt, sentAt));
    boolean tooEarly = derived.isBefore(receivedAt.minus(MAX_EARLY));
    boolean tooLate = derived.isAfter(receivedAt.plus(MAX_LATE));
    return tooEarly || tooLate ? receivedAt : derived;
  }
}
