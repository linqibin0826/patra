package dev.patra.notification.domain.mail.model.vo.content;

import java.util.List;

/// 每日汇总一节的事实：窗口里的未读总数与能列出的几条。
///
/// @param total 未读总数
/// @param listable 能列出的（有落点的）几条
public record DigestSectionFacts(int total, List<ReminderFacts> listable) {

  /// 防御性拷贝。
  public DigestSectionFacts {
    listable = List.copyOf(listable);
  }

  /// 构造。
  ///
  /// @param total 未读总数
  /// @param listable 能列出的几条
  /// @return 一节的事实
  public static DigestSectionFacts of(int total, List<ReminderFacts> listable) {
    return new DigestSectionFacts(total, listable);
  }
}
