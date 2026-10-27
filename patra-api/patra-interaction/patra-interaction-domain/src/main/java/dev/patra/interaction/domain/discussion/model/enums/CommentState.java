package dev.patra.interaction.domain.discussion.model.enums;

/// 评论的状态。
public enum CommentState {
  /// 正常。
  ACTIVE,
  /// 已删除、留下占位：删除时还有回复，正文与修改历史已清掉，楼中楼保留。
  DELETED
}
