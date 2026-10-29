package dev.patra.catalog.infra.organization.persistence;

/// `OrganizationDao#findSummaryByRorId` native query 的接口投影。
///
/// getter 名与 SQL 列别名一一对应（别名在 SQL 中加双引号保持 camelCase）。
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationSummaryRow {

  String getRorId();

  String getDisplayName();

  String getZhName();

  String getCountryCode();

  String getCityName();

  /// ROR 类型码，空格分隔（`array_to_string`），没有为空串或 `null`。
  ///
  /// @return 空格分隔的类型码
  String getTypes();
}
