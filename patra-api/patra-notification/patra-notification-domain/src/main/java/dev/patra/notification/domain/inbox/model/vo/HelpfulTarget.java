package dev.patra.notification.domain.inbox.model.vo;

import dev.patra.notification.domain.inbox.model.enums.HelpfulTargetType;
import java.util.Objects;

/// 被点有帮助的那条内容，也是合并键的一部分。
///
/// @param type 评论或投稿经验
/// @param targetId 评论 ID 或帖子 ID
/// @param subjectId 所在对象（interaction 登记表 ID）；点经验时是经验自己
public record HelpfulTarget(HelpfulTargetType type, long targetId, long subjectId) {

  /// 校验非空。
  public HelpfulTarget {
    Objects.requireNonNull(type, "type 不能为 null");
  }

  /// 构造。
  ///
  /// @param type 种类
  /// @param targetId 目标 ID
  /// @param subjectId 所在对象
  /// @return 目标
  public static HelpfulTarget of(HelpfulTargetType type, long targetId, long subjectId) {
    return new HelpfulTarget(type, targetId, subjectId);
  }

  /// 信箱行上的评论列：点评论时是评论 ID，点经验时为空。
  ///
  /// @return 评论 ID，可空
  public Long commentId() {
    return type == HelpfulTargetType.COMMENT ? targetId : null;
  }
}
