package dev.patra.notification.domain.inbox.model.read;

import java.util.List;

/// 一个类别在时间窗口里的未读：总数与最新的几条（每日汇总用）。
///
/// @param total 窗口里的未读总数
/// @param newest 排序时间最新的几条，新的在前
public record UnreadWindow(int total, List<InboxEntry> newest) {

  /// 防御性拷贝。
  public UnreadWindow {
    newest = List.copyOf(newest);
  }

  /// 构造。
  ///
  /// @param total 总数
  /// @param newest 最新的几条
  /// @return 窗口
  public static UnreadWindow of(int total, List<InboxEntry> newest) {
    return new UnreadWindow(total, newest);
  }
}
