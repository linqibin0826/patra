package dev.patra.interaction.app.notification.usecase.query;

import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import java.time.Instant;
import lombok.Builder;

/// 提醒列表的一行。
///
/// @param notificationId 提醒 ID
/// @param type 类型
/// @param read 是否已读
/// @param createdAt 生成时刻
/// @param actor 触发人名片；匿名所有者触发或 identity 不认识（已注销）时为空
/// @param actorAnonymous 触发人是匿名所有者
/// @param subject 对象上下文；登记表里找不到时为空
/// @param commentId 评论
/// @param excerpt 纯文本摘录（≤ 120 字素）；对象不可用（文献下线）时为空
/// @param excerptTruncated 摘录是否截断过
@Builder
public record NotificationView(
    long notificationId,
    NotificationType type,
    boolean read,
    Instant createdAt,
    UserCard actor,
    boolean actorAnonymous,
    SubjectRefView subject,
    Long commentId,
    String excerpt,
    boolean excerptTruncated) {}
