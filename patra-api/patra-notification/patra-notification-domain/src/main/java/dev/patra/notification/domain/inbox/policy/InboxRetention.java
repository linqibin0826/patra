package dev.patra.notification.domain.inbox.policy;

import java.time.Instant;
import java.time.ZoneId;

/// 信箱的保留期：5 个月，按北京时间的自然月算。提醒、评论快照与墓碑共用同一个截止时刻。
public final class InboxRetention {

  /// 保留的月数。
  public static final int MONTHS = 5;

  /// 算自然月用的时区。
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  /// 只有静态方法，不实例化。
  private InboxRetention() {}

  /// 清理的截止时刻：早于它的删掉。
  ///
  /// @param now 当前时刻
  /// @return 北京时间减 5 个自然月（月末对齐到目标月最后一天）
  public static Instant cutoff(Instant now) {
    return now.atZone(ZONE).minusMonths(MONTHS).toInstant();
  }
}
