package dev.patra.interaction.domain.reaction.model.enums;

/// 能点有帮助的目标种类，按名字存进 `reaction` 的表（`'COMMENT'` / `'POST'`）。文献、收藏夹不能点。
public enum HelpfulTargetType {
  /// 评论：根评论与楼内回复，ID 是 `discussion.comment.comment_id`。
  COMMENT,
  /// 投稿经验：ID 是 `post.post.post_id`。
  POST
}
