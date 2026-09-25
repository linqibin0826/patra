package dev.linqibin.patra.catalog.infra.adapter.read;

/// [dev.linqibin.patra.catalog.infra.persistence.dao.PublicationSearchDao] facet 聚合查询的接口投影
/// （`value` / `count`）。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationFacetCountRow {

  String getValue();

  long getCount();
}
