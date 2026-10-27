package dev.patra.interaction.domain.subject.model.enums;

/// 对象能被做的互动。能力矩阵在 {@link SubjectType} 里。
public enum SubjectCapability {
  /// 加入文献库（收藏）。
  COLLECT,

  /// 发表评论与回复（讨论）。
  DISCUSS,

  /// 挂投稿经验：在期刊下写投稿经验。
  SUBMISSION_EXPERIENCE
}
