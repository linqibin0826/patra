package dev.patra.interaction.domain.post.model.enums;

/// 投稿经验的最终结果（spec 2.1.2）。录用率 = 录用 ÷（录用 + 拒稿），撤稿与进行中不计入。
public enum FinalOutcome {
  /// 录用。
  ACCEPTED,
  /// 拒稿。
  REJECTED,
  /// 撤稿。
  WITHDRAWN,
  /// 进行中：不填最终决定日期。
  IN_PROGRESS
}
