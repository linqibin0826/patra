package dev.patra.analytics.simulator.truth;

import java.time.LocalDate;

/// 一天一篇文献的真值（或 ClickHouse 算出的对应数）。
///
/// @param day 北京时间的日期
/// @param articleId 文献 ID
/// @param viewers 看过的人数
/// @param fulltextReaders 读过全文的人数
public record TruthRow(LocalDate day, long articleId, int viewers, int fulltextReaders) {}
