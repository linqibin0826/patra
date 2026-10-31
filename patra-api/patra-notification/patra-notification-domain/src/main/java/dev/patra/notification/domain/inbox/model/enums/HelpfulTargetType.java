package dev.patra.notification.domain.inbox.model.enums;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import java.util.Arrays;
import java.util.Optional;

/// 被点有帮助的内容是什么：interaction 的 `helpful.marked` 里 `targetType` 的两个值。
public enum HelpfulTargetType {

  /// 评论（根评论与楼内回复都算）：目标 ID 是评论 ID。
  COMMENT(NotificationType.COMMENT_HELPFUL),

  /// 投稿经验：目标 ID 是帖子 ID。
  POST(NotificationType.POST_HELPFUL);

  private final NotificationType notificationType;

  /// 构造。
  ///
  /// @param notificationType 对应的提醒类型
  HelpfulTargetType(NotificationType notificationType) {
    this.notificationType = notificationType;
  }

  /// 合并条目的提醒类型。
  ///
  /// @return `COMMENT_HELPFUL` 或 `POST_HELPFUL`
  public NotificationType notificationType() {
    return notificationType;
  }

  /// 按名字解析，只认大写的名字；不认识的值（interaction 以后加了别的目标）为空，由调用方确认后跳过。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 种类；不认识时为空
  public static Optional<HelpfulTargetType> parse(String raw) {
    return Arrays.stream(values()).filter(t -> t.name().equals(raw)).findFirst();
  }
}
