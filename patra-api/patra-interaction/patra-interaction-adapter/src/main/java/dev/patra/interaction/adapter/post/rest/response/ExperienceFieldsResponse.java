package dev.patra.interaction.adapter.post.rest.response;

import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import java.time.LocalDate;
import lombok.Builder;

/// 经验的结构化字段与算出的时长（展开进条目与修改历史的每一版）。
///
/// @param submittedOn 投稿日期
/// @param firstDecisionOn 首轮决定日期
/// @param firstDecision 首轮结果
/// @param finalOutcome 最终结果
/// @param finalDecisionOn 最终决定日期，可空
/// @param articleType 文章类型
/// @param reviewRounds 审稿轮数，可空
/// @param reviewQuality 审稿质量，可空
/// @param editorCommunication 编辑沟通，可空
/// @param apcPaid 是否付 APC，可空
/// @param previouslyRejected 是否转投过，可空
/// @param firstRoundDays 首轮用时（天）
/// @param totalDays 投稿到最终决定（天），没有时为 `null`
@Builder
public record ExperienceFieldsResponse(
    LocalDate submittedOn,
    LocalDate firstDecisionOn,
    String firstDecision,
    String finalOutcome,
    LocalDate finalDecisionOn,
    String articleType,
    Integer reviewRounds,
    Integer reviewQuality,
    Integer editorCommunication,
    Boolean apcPaid,
    Boolean previouslyRejected,
    int firstRoundDays,
    Integer totalDays) {

  /// 转换。
  ///
  /// @param f 字段
  /// @return 响应
  public static ExperienceFieldsResponse from(ExperienceFields f) {
    return ExperienceFieldsResponse.builder()
        .submittedOn(f.submittedOn())
        .firstDecisionOn(f.firstDecisionOn())
        .firstDecision(f.firstDecision().name())
        .finalOutcome(f.finalOutcome().name())
        .finalDecisionOn(f.finalDecisionOn())
        .articleType(f.articleType().name())
        .reviewRounds(f.reviewRounds())
        .reviewQuality(f.reviewQuality())
        .editorCommunication(f.editorCommunication())
        .apcPaid(f.apcPaid())
        .previouslyRejected(f.previouslyRejected())
        .firstRoundDays(f.firstRoundDays())
        .totalDays(f.totalDays())
        .build();
  }
}
