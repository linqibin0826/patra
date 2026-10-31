package dev.patra.notification.domain.mail.model.vo.content;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 拼一条提醒要用的事实：信箱里的条目加上现查的名字与对象视图。
///
/// @param notificationId 提醒 ID
/// @param type 类型
/// @param actorName 触发人的名字（已按 spec 8.1 定好）
/// @param occurredAt 排序时间（评论发表或编辑的时刻），邮件里显示它
/// @param plainText 评论纯文本全文，含 `<@ID>`
/// @param subject 所在对象的视图，有 `key` 且可用
/// @param commentId 评论
/// @param mine 对象是不是收件人自己的投稿经验
@Builder
public record ReminderFacts(
    long notificationId,
    NotificationType type,
    String actorName,
    Instant occurredAt,
    String plainText,
    SubjectView subject,
    long commentId,
    boolean mine) {

  /// 校验非空。
  public ReminderFacts {
    Objects.requireNonNull(type, "type 不能为 null");
    Objects.requireNonNull(actorName, "actorName 不能为 null");
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
    Objects.requireNonNull(plainText, "plainText 不能为 null");
    Objects.requireNonNull(subject, "subject 不能为 null");
  }
}
