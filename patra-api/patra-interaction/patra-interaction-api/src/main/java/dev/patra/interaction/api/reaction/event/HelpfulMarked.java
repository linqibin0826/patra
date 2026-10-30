package dev.patra.interaction.api.reaction.event;

import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.tool.core.event.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/// 集成事件：有帮助已点（交换机 `interaction.events`，路由键 {@value #ROUTING_KEY}，PAP-141）。只在真插入了一条明细时发：
/// 同一人点、取消、再点会发两次（两个不同的 `eventId`），按人去重由消费方做。有帮助已取消不外送。
///
/// @param eventId 事件 ID：补投、重投都不变，消费方据此去重
/// @param targetType 目标种类：`COMMENT` / `POST`
/// @param targetId 评论 ID 或帖子 ID
/// @param targetAuthorId 被点内容的作者 = 收件人（匿名经验也是真实作者）
/// @param targetAnonymous 匿名经验，或作者在自己匿名经验下发的评论
/// @param userId 点的人
/// @param markedAt 点的时刻（interaction 写库时取，补投不变）；按北京时间切天用它，不用消费时间
/// @param subject 被点内容所在的对象；点经验时是经验自己
/// @param experience 目标是投稿经验时它的结果；目标是评论为 `null`
/// @param targetPlainText 被点内容的全文纯文本（PAP-128）；经验没写正文时为 `null`，摘录行改写结果
@Builder
public record HelpfulMarked(
    UUID eventId,
    String targetType,
    long targetId,
    long targetAuthorId,
    boolean targetAnonymous,
    long userId,
    Instant markedAt,
    SubjectContext subject,
    ExperienceOutcome experience,
    String targetPlainText)
    implements IntegrationEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "helpful.marked";
}
