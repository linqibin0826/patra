package dev.patra.catalog.infra.organization.persistence;

/// `OrganizationNameDao` 两个建议查询的接口投影：摘要列加上命中的名称。
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationSuggestRow extends OrganizationSummaryRow {

  String getMatchedName();
}
