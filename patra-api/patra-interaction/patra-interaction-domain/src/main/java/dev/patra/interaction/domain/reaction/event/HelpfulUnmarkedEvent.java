package dev.patra.interaction.domain.reaction.event;

import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 有帮助已取消：真删掉了一条明细时，有帮助子域在同一事务里发布（没点过就取消不发）。
///
/// 只在服务内发布、不外送（PAP-141）：产品规则是取消不撤回提醒、人数不减，通知服务没有动作，没有队列绑定的事件
/// 会被 broker 退回、一直补投。以后有了消费方，再按它的需要补字段、加进外送配置。
///
/// @param eventId 事件 ID：发布时生成
/// @param targetType 目标种类
/// @param targetId 评论 ID 或帖子 ID
/// @param userId 取消的人
/// @param unmarkedAt 取消的时刻
@Builder
public record HelpfulUnmarkedEvent(
    UUID eventId, HelpfulTargetType targetType, long targetId, long userId, Instant unmarkedAt) {

  /// 事件 ID 必填。
  public HelpfulUnmarkedEvent {
    Objects.requireNonNull(eventId, "eventId");
  }
}
