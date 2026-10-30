package dev.patra.interaction.api.post.event;

import dev.patra.tool.core.event.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/// 集成事件：投稿经验已删除（交换机 `interaction.events`，路由键 {@value #ROUTING_KEY}，PAP-141）。已删除再删不发。
///
/// 经验下的评论不会随之发「评论已删除」（评论没被删）；消费方按 `subjectId` 删掉这条经验下的全部提醒，并记墓碑：
/// 之后到的同一对象的评论事件不再生成提醒。
///
/// @param eventId 事件 ID：补投、重投都不变，消费方据此去重
/// @param postId 帖子 ID
/// @param subjectId 这条经验自己的登记对象（它下面评论的 `subject.subjectId`），不是所属期刊
/// @param authorId 作者
/// @param deletedAt 删除时刻
@Builder
public record PostDeleted(
    UUID eventId, long postId, long subjectId, long authorId, Instant deletedAt)
    implements IntegrationEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "post.deleted";
}
