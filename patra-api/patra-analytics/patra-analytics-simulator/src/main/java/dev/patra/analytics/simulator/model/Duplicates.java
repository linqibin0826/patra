package dev.patra.analytics.simulator.model;

import java.util.UUID;

/// 故意重发的 1%（spec §5.6）：由事件 ID 决定的纯函数，两种模式共用，重跑时选中的事件不变。
public final class Duplicates {

  private static final long ONE_IN = 100;

  private Duplicates() {}

  /// 这条事件要不要再发一次。
  ///
  /// @param eventId 事件 ID
  /// @return 是否重发
  public static boolean chosen(UUID eventId) {
    return Long.remainderUnsigned(eventId.getLeastSignificantBits(), ONE_IN) == 0;
  }
}
