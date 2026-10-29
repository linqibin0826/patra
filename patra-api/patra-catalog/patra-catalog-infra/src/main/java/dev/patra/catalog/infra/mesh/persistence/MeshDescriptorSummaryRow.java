package dev.patra.catalog.infra.mesh.persistence;

/// `MeshDescriptorDao#findSummaryByUi` native query 的接口投影。
///
/// getter 名与 SQL 列别名一一对应（别名在 SQL 中加双引号保持 camelCase）。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorSummaryRow {

  String getUi();

  String getName();

  /// 全部树号，空格分隔（`string_agg`），主树号在前；没有树号为 `null`。
  ///
  /// @return 空格分隔的树号
  String getTreeNumbers();
}
