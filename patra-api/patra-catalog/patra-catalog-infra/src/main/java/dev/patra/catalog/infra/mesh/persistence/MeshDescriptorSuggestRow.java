package dev.patra.catalog.infra.mesh.persistence;

/// `MeshDescriptorDao#suggest` native query 的接口投影：摘要列加上命中的入口词。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorSuggestRow extends MeshDescriptorSummaryRow {

  /// 经入口词命中时的入口词原文；命中的是主题词名本身（或入口词与名称只差大小写）时为 `null`。
  ///
  /// @return 入口词原文或 `null`
  String getMatchedTerm();
}
