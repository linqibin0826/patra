package dev.patra.interaction.domain.post.model.vo;

import lombok.Builder;

/// 用户填的投稿经验原文：日期与枚举是字符串，由 {@link ExperienceDraft#parse} 解析成字段错误或 {@link ExperienceFields}。
///
/// @param submittedOn 投稿日期 `yyyy-MM-dd`
/// @param firstDecisionOn 首轮决定日期
/// @param firstDecision 首轮结果
/// @param finalOutcome 最终结果
/// @param finalDecisionOn 最终决定日期，可空
/// @param articleType 文章类型
/// @param reviewRounds 审稿轮数，可空
/// @param reviewQuality 审稿质量评分，可空
/// @param editorCommunication 编辑沟通评分，可空
/// @param apcPaid 是否付 APC，可空
/// @param previouslyRejected 是否转投过（投本刊之前被别的期刊拒过），可空
/// @param body 正文 Markdown，可空
@Builder
public record ExperienceInput(
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
    String body) {}
