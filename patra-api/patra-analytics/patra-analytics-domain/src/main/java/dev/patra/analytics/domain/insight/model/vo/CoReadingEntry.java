package dev.patra.analytics.domain.insight.model.vo;

import lombok.Builder;

/// 共现快照的一行：文献 X 的第 `position` 篇推荐。
///
/// @param articleId 文献 X
/// @param position 名次，1 ~ 20
/// @param relatedArticleId 推荐的文献
/// @param similarity 余弦相似度
/// @param coReaders 一起读过的人数（只供排查，不对外）
@Builder
public record CoReadingEntry(
    long articleId, int position, long relatedArticleId, double similarity, int coReaders) {}
