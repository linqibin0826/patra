package dev.patra.catalog.infra.adapter.read;

/// [VenueDetailReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @author linqibin
/// @since 0.1.0
record CasRatingRow(
    Integer year,
    String edition,
    String majorCategory,
    String majorQuartile,
    String minorSubject,
    String minorQuartile,
    Boolean isTop,
    Boolean isReview) {}
