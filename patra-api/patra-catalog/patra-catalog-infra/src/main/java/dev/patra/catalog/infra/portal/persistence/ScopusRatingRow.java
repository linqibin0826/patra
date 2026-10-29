package dev.patra.catalog.infra.portal.persistence;

import java.math.BigDecimal;

/// [VenueDetailReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @author linqibin
/// @since 0.1.0
record ScopusRatingRow(
    Integer year,
    BigDecimal citeScore,
    BigDecimal sjr,
    BigDecimal snip,
    String quartile,
    BigDecimal percentile) {}
