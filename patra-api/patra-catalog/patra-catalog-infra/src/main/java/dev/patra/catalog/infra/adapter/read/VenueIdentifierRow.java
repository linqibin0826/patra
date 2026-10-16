package dev.patra.catalog.infra.adapter.read;

/// `VenueDao#findIdentifiersByVenueId` native query 的 Spring Data 接口投影。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueIdentifierRow {

  String getType();

  String getValue();

  Boolean getPrimary();
}
