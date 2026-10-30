package dev.patra.interaction.domain.reaction.event;

import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 有帮助已点：真插入了一条明细时，有帮助子域在同一事务里发布（重复点不发）。外送成集成事件 `helpful.marked`，
/// 通知服务据此给内容的作者写有帮助提醒（PAP-141 / PAP-144）。
///
/// 被点内容的纯文本与经验的结果随事件带：提醒条目的第二行是「你那条内容」的摘录，通知服务手里没有这条内容。
///
/// @param eventId 事件 ID：发布时生成，外送与补投原样带上，消费方据此去重
/// @param targetType 目标种类
/// @param targetId 评论 ID 或帖子 ID
/// @param targetAuthorId 被点内容的作者（匿名经验也是真实作者）
/// @param targetAnonymous 匿名经验，或作者在自己匿名经验下发的评论
/// @param userId 点的人
/// @param markedAt 点的时刻（写进明细的那一刻，补投不变）
/// @param subjectId 被点内容所在的对象；点经验时是经验自己的登记对象
/// @param subjectType 对象种类
/// @param subjectOwner 对象的所有者与是否匿名；文献为 `null`
/// @param experience 目标是投稿经验时它的结果；目标是评论、或经验查不到时为 `null`
/// @param targetPlainText 被点内容的全文纯文本（PAP-128）；经验没写正文、或查不到时为 `null`
@Builder
public record HelpfulMarkedEvent(
    UUID eventId,
    HelpfulTargetType targetType,
    long targetId,
    long targetAuthorId,
    boolean targetAnonymous,
    long userId,
    Instant markedAt,
    SubjectId subjectId,
    SubjectType subjectType,
    SubjectOwner subjectOwner,
    ExperienceOutline experience,
    String targetPlainText) {

  /// 事件 ID 必填：不在这里兜底生成，否则补投读回登记行时会换一个 ID。
  public HelpfulMarkedEvent {
    Objects.requireNonNull(eventId, "eventId");
  }
}
