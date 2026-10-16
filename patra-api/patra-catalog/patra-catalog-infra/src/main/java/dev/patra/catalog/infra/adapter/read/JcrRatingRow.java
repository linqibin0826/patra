package dev.patra.catalog.infra.adapter.read;

import java.math.BigDecimal;

/// `VenueDao#findJcrRatingsByVenueId` native query 的 Spring Data 接口投影。
///
/// @author linqibin
/// @since 0.1.0
public interface JcrRatingRow {

  Integer getYear();

  BigDecimal getImpactFactor();

  String getQuartile();

  String getSubject();

  String getJifRank();

  BigDecimal getJifPercentile();
}
