package dev.patra.catalog.infra.adapter.read;

/// `VenueDao` facet 聚合查询的 Spring Data 接口投影。
///
/// getter 名与 SQL 列别名一一对应（`value` / `count`）。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueFacetCountRow {

  String getValue();

  long getCount();
}
