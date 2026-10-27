package dev.patra.interaction.domain.post.model.enums;

import java.util.Optional;

/// 投稿经验的首轮结果（spec 2.1.2）。直接拒稿、审后拒稿、录用是**终局**：首轮就是最终决定（spec 4.2）。
public enum FirstDecision {
  /// 直接拒稿（没有送审）：最终只能是拒稿。
  DESK_REJECT(FinalOutcome.REJECTED),
  /// 审后拒稿：最终只能是拒稿。
  REJECT_AFTER_REVIEW(FinalOutcome.REJECTED),
  /// 大修。
  MAJOR_REVISION(null),
  /// 小修。
  MINOR_REVISION(null),
  /// 首轮录用：最终只能是录用。
  ACCEPT(FinalOutcome.ACCEPTED);

  private final FinalOutcome impliedOutcome;

  /// 定义一种首轮结果。
  ///
  /// @param impliedOutcome 终局时决定的最终结果，修改类为 `null`
  FirstDecision(FinalOutcome impliedOutcome) {
    this.impliedOutcome = impliedOutcome;
  }

  /// 终局时决定的最终结果。
  ///
  /// @return 最终结果；修改类为空
  public Optional<FinalOutcome> impliedOutcome() {
    return Optional.ofNullable(impliedOutcome);
  }

  /// 首轮是否就是最终决定。
  ///
  /// @return 终局时为 `true`
  public boolean isTerminal() {
    return impliedOutcome != null;
  }
}
