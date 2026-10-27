package dev.patra.interaction.domain.post.model.vo;

import dev.patra.interaction.domain.post.model.enums.ArticleType;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.Builder;

/// 投稿经验的结构化字段（经验扩展表的一行，也是修改历史里的一版）。只收日期，时长由这里算。
///
/// @param submittedOn 投稿日期
/// @param firstDecisionOn 首轮决定日期
/// @param firstDecision 首轮结果
/// @param finalOutcome 最终结果
/// @param finalDecisionOn 最终决定日期，可空
/// @param articleType 文章类型
/// @param reviewRounds 审稿轮数，可空
/// @param reviewQuality 审稿质量 1–5，可空
/// @param editorCommunication 编辑沟通 1–5，可空
/// @param apcPaid 是否付 APC，可空
/// @param previouslyRejected 是否转投过（投本刊之前被别的期刊拒过），可空
@Builder(toBuilder = true)
public record ExperienceFields(
    LocalDate submittedOn,
    LocalDate firstDecisionOn,
    FirstDecision firstDecision,
    FinalOutcome finalOutcome,
    LocalDate finalDecisionOn,
    ArticleType articleType,
    Integer reviewRounds,
    Integer reviewQuality,
    Integer editorCommunication,
    Boolean apcPaid,
    Boolean previouslyRejected) {

  /// 首轮用时：首轮决定日期 − 投稿日期。
  ///
  /// @return 天数
  public int firstRoundDays() {
    return (int) ChronoUnit.DAYS.between(submittedOn, firstDecisionOn);
  }

  /// 投稿到最终决定的天数：有最终决定日期按它算；首轮终局等于首轮用时；修改类没填最终日期为 `null`。
  ///
  /// @return 天数，可空
  public Integer totalDays() {
    if (finalDecisionOn != null) {
      return (int) ChronoUnit.DAYS.between(submittedOn, finalDecisionOn);
    }
    return firstDecision != null && firstDecision.isTerminal() ? firstRoundDays() : null;
  }

  /// 投稿月份 `yyyy-MM`（登记快照的 `publishedDate`，提醒的上下文行用）。
  ///
  /// @return 月份
  public String submittedMonth() {
    return submittedOn.toString().substring(0, 7);
  }
}
