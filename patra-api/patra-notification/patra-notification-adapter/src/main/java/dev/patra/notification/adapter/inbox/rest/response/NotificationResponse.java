package dev.patra.notification.adapter.inbox.rest.response;

import dev.patra.notification.app.inbox.usecase.query.NotificationView;
import java.time.Instant;
import lombok.Builder;

/// 提醒列表的一行（spec 8.2）。
///
/// @param notificationId 提醒 ID
/// @param type `COMMENT_REPLY` / `POST_COMMENT` / `MENTION`（有帮助的类型由 PAP-144 加）
/// @param read 是否已读
/// @param occurredAt 排序时间（有帮助条目再被点时会变）
/// @param actor 触发人；匿名作者或已注销用户时为空
/// @param actorAnonymous 触发人是匿名作者（为 `true` 时 `actor` 恒为空）
/// @param subject 对象上下文；查不到或 interaction 挂了时为空
/// @param commentId 评论（永久链接用）；有帮助点经验时为空
/// @param excerpt 纯文本摘录 ≤ 120 字素；对象为空或不可用时为空
/// @param excerptTruncated 摘录截断过（门户在末尾加省略号）
@Builder
public record NotificationResponse(
    Long notificationId,
    String type,
    boolean read,
    Instant occurredAt,
    ActorResponse actor,
    boolean actorAnonymous,
    SubjectRefResponse subject,
    Long commentId,
    String excerpt,
    boolean excerptTruncated) {

  /// 转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static NotificationResponse from(NotificationView view) {
    return NotificationResponse.builder()
        .notificationId(view.notificationId())
        .type(view.type().name())
        .read(view.read())
        .occurredAt(view.occurredAt())
        .actor(ActorResponse.from(view.actor()))
        .actorAnonymous(view.actorAnonymous())
        .subject(SubjectRefResponse.from(view.subject()))
        .commentId(view.commentId())
        .excerpt(view.excerpt())
        .excerptTruncated(view.excerptTruncated())
        .build();
  }
}
