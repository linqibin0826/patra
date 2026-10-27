package dev.patra.interaction.adapter.post.rest.request;

import dev.patra.interaction.domain.post.model.vo.ExperienceInput;
import lombok.Builder;

/// 写、改投稿经验的请求体。日期 `yyyy-MM-dd`，枚举写常量名；`anonymous` 只在写入时生效，编辑时忽略。
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
/// @param body 正文 Markdown，可空
/// @param anonymous 是否匿名发布，缺省 false
@Builder
public record ExperienceRequest(
    String submittedOn,
    String firstDecisionOn,
    String firstDecision,
    String finalOutcome,
    String finalDecisionOn,
    String articleType,
    Integer reviewRounds,
    Integer reviewQuality,
    Integer editorCommunication,
    Boolean apcPaid,
    Boolean previouslyRejected,
    String body,
    Boolean anonymous) {

  /// 填写内容。
  ///
  /// @return 输入
  public ExperienceInput toInput() {
    return ExperienceInput.builder()
        .submittedOn(submittedOn)
        .firstDecisionOn(firstDecisionOn)
        .firstDecision(firstDecision)
        .finalOutcome(finalOutcome)
        .finalDecisionOn(finalDecisionOn)
        .articleType(articleType)
        .reviewRounds(reviewRounds)
        .reviewQuality(reviewQuality)
        .editorCommunication(editorCommunication)
        .apcPaid(apcPaid)
        .previouslyRejected(previouslyRejected)
        .body(body)
        .build();
  }

  /// 是否匿名（缺省 false）。
  ///
  /// @return 匿名时为 `true`
  public boolean anonymousOrFalse() {
    return Boolean.TRUE.equals(anonymous);
  }
}
