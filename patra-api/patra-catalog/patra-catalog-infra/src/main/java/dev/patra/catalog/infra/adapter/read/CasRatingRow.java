package dev.patra.catalog.infra.adapter.read;

/// `VenueDao#findCasRatingsByVenueId` native query 的 Spring Data 接口投影。
///
/// @author linqibin
/// @since 0.1.0
public interface CasRatingRow {

  Integer getYear();

  String getEdition();

  String getMajorCategory();

  String getMajorQuartile();

  String getMinorSubject();

  String getMinorQuartile();

  Boolean getIsTop();

  Boolean getIsReview();
}
