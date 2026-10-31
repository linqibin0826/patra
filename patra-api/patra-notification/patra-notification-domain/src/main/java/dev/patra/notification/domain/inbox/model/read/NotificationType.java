package dev.patra.notification.domain.inbox.model.read;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.util.Arrays;
import java.util.List;

/// 提醒的动作类型；每个类型归一个类别（提醒页的标签）。
public enum NotificationType {

  /// 回复了你的评论。
  COMMENT_REPLY(NotificationCategory.REPLY),

  /// 评论了你的投稿经验（经验下的根评论）。
  POST_COMMENT(NotificationCategory.REPLY),

  /// 在评论中 @ 了你。
  MENTION(NotificationCategory.MENTION),

  /// 有人觉得你的评论有帮助（同一条评论按北京时间一天合并成一条，PAP-144）。
  COMMENT_HELPFUL(NotificationCategory.HELPFUL),

  /// 有人觉得你的投稿经验有帮助（同一篇经验按北京时间一天合并成一条，PAP-144）。
  POST_HELPFUL(NotificationCategory.HELPFUL);

  private final NotificationCategory category;

  /// 构造。
  ///
  /// @param category 所属类别
  NotificationType(NotificationCategory category) {
    this.category = category;
  }

  /// 所属类别。
  ///
  /// @return 类别
  public NotificationCategory category() {
    return category;
  }

  /// 一个类别下的全部类型。
  ///
  /// @param category 类别
  /// @return 类型，按声明顺序
  public static List<NotificationType> of(NotificationCategory category) {
    return Arrays.stream(values()).filter(t -> t.category == category).toList();
  }
}
