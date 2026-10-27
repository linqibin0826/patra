package dev.patra.interaction.domain.discussion.model.enums;

/// 删除一条评论要做什么。
public enum CommentRemoval {
  /// 还有直接回复：变占位，清掉正文与修改历史，楼中楼保留。
  PLACEHOLDER,
  /// 没有直接回复：整条去掉，再看父评论是不是已经没有回复的占位。
  REMOVE,
  /// 已经是占位：什么都不做（幂等）。
  NONE
}
