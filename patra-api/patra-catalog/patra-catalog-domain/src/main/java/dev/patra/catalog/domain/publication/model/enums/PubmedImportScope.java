package dev.patra.catalog.domain.publication.model.enums;

/// PubMed 增量同步的导入范围。
///
/// - **EXISTING_ONLY** - 只更新库里已有的文献，库里没有的记录跳过（开发环境 mini 用）
/// - **ALL** - 已有的覆盖更新，没有的新建
///
/// @author linqibin
/// @since 0.11.0
public enum PubmedImportScope {

  /// 只更新已有文献
  EXISTING_ONLY,

  /// 已有的更新、没有的新建
  ALL
}
