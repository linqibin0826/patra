package dev.patra.notification.app.inbox.usecase.query;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import java.time.Instant;
import lombok.Builder;

/// 提醒列表的一行。
///
/// @param notificationId 提醒 ID
/// @param type 类型
/// @param read 是否已读
/// @param occurredAt 排序时间
/// @param actor 触发人名片；匿名作者触发或 identity 不认识（已注销）时为空
/// @param actorAnonymous 触发人是匿名作者
/// @param subject 对象上下文；查不到或 interaction 挂了时为空
/// @param commentId 评论；有帮助点经验时为空
/// @param excerpt 纯文本摘录（≤ 120 字素、单行）；对象为空或不可用时为空
/// @param excerptTruncated 摘录是否截断过
@Builder
public record NotificationView(
    long notificationId,
    NotificationType type,
    boolean read,
    Instant occurredAt,
    UserCard actor,
    boolean actorAnonymous,
    SubjectRefView subject,
    Long commentId,
    String excerpt,
    boolean excerptTruncated) {}
