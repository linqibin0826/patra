package dev.patra.interaction.domain.post.model.enums;

/// 帖子状态。删除是软删：内容留在库里，不再展示。
public enum PostStatus {
  /// 正常。
  ACTIVE,
  /// 作者已删除。
  DELETED
}
