package dev.patra.notification.domain.inbox.policy;

import java.time.Instant;
import java.time.LocalDate;

/// 有帮助合并键里的「那一天」：点击时刻在北京时间的日期（PAP-144 spec 2.2）。
///
/// 只看事件里的点击时刻（事件时间），不看通知服务处理消息的时刻：补投、重试会把 23:59 的点击拖到第二天才处理，
/// 按处理时刻切天就会算错。
public final class HelpfulDay {

  /// 只有静态方法，不实例化。
  private HelpfulDay() {}

  /// 点击时刻属于北京时间的哪一天。
  ///
  /// @param markedAt 点击时刻
  /// @return 北京时间的日期
  public static LocalDate of(Instant markedAt) {
    return markedAt.atZone(InboxRetention.ZONE).toLocalDate();
  }
}
