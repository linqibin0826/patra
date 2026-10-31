package dev.patra.notification.domain.mail.model.vo.content;

import java.util.List;

/// 每日汇总的一节（`回复我的 · 3 条`）。
///
/// @param title 节标题，写这一节的未读总数
/// @param items 列出的提醒，新的在前
public record DigestSection(String title, List<ReminderItem> items) {

  /// 防御性拷贝。
  public DigestSection {
    items = List.copyOf(items);
  }

  /// 构造。
  ///
  /// @param title 节标题
  /// @param items 列出的提醒
  /// @return 一节
  public static DigestSection of(String title, List<ReminderItem> items) {
    return new DigestSection(title, items);
  }
}
