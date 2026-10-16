package dev.patra.catalog.infra.adapter.read;

import java.math.BigDecimal;

/// `VenueDao#findScopusRatingsByVenueId` native query 的 Spring Data 接口投影。
///
/// @author linqibin
/// @since 0.1.0
public interface ScopusRatingRow {

  Integer getYear();

  BigDecimal getCiteScore();

  BigDecimal getSjr();

  BigDecimal getSnip();

  String getQuartile();

  BigDecimal getPercentile();
}
