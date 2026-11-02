package dev.patra.analytics.domain.insight.model.vo;

import lombok.Builder;

/// 热读快照的一行：上榜的在前（名次 = `position`），最新收录补位的接在后面。
///
/// @param position 位置，1 起
/// @param articleId 文献 ID
/// @param ranked 上榜为 true，补位为 false
/// @param score 分数；补位为 null
/// @param readers7d 近 7 天人数；补位为 null
@Builder
public record HotReadingEntry(
    int position, long articleId, boolean ranked, Double score, Integer readers7d) {}
