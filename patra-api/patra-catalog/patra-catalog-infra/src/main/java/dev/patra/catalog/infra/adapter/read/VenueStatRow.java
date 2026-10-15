package dev.patra.catalog.infra.adapter.read;

/// `VenueDao#findPublicationStatsByVenueId` native query 的 Spring Data 接口投影。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueStatRow {

  Integer getYear();

  Integer getWorksCount();

  Integer getCitedByCount();

  Integer getOaWorksCount();
}
