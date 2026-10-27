package dev.patra.interaction.domain.notification.model.enums;

import java.util.Arrays;
import java.util.List;

/// 提醒类型；按信箱设计，以后通知中心的其他类型接着往这里加。
public enum NotificationType {

  /// 回复了你的评论。
  COMMENT_REPLY(NotificationTab.REPLIES),

  /// 评论了你的投稿经验（经验下的根评论）。
  POST_COMMENT(NotificationTab.REPLIES),

  /// 在评论中 @ 了你。
  MENTION(NotificationTab.MENTIONS);

  private final NotificationTab tab;

  /// 构造。
  ///
  /// @param tab 所在标签
  NotificationType(NotificationTab tab) {
    this.tab = tab;
  }

  /// 所在标签。
  ///
  /// @return 标签
  public NotificationTab tab() {
    return tab;
  }

  /// 一个标签下的全部类型。
  ///
  /// @param tab 标签
  /// @return 类型，按声明顺序
  public static List<NotificationType> of(NotificationTab tab) {
    return Arrays.stream(values()).filter(t -> t.tab == tab).toList();
  }
}
