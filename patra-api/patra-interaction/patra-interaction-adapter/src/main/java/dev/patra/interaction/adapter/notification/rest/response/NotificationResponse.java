package dev.patra.interaction.adapter.notification.rest.response;

import dev.patra.interaction.app.notification.usecase.query.NotificationView;
import java.time.Instant;
import lombok.Builder;

/// 提醒列表的一行（spec 9）。
///
/// @param notificationId 提醒 ID
/// @param type `COMMENT_REPLY` / `POST_COMMENT` / `MENTION`
/// @param read 是否已读
/// @param createdAt 生成时刻
/// @param actor 触发人；匿名作者或已注销用户时为空
/// @param actorAnonymous 触发人是匿名作者（为 `true` 时 `actor` 恒为空）
/// @param subject 对象上下文
/// @param commentId 评论（永久链接用）
/// @param excerpt 纯文本摘录 ≤ 120 字素；对象不可用（文献下线）时为空
/// @param excerptTruncated 摘录截断过
@Builder
public record NotificationResponse(
    Long notificationId,
    String type,
    boolean read,
    Instant createdAt,
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
        .createdAt(view.createdAt())
        .actor(ActorResponse.from(view.actor()))
        .actorAnonymous(view.actorAnonymous())
        .subject(SubjectRefResponse.from(view.subject()))
        .commentId(view.commentId())
        .excerpt(view.excerpt())
        .excerptTruncated(view.excerptTruncated())
        .build();
  }
}
