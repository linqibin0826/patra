package dev.patra.catalog.domain.publication.model.enums;

/// PubMed 文件的种类：年度 baseline 或每日 updatefiles。两者同一年共用一个编号序列，下载目录不同。
///
/// @author linqibin
/// @since 0.1.0
public enum PubmedFileKind {

  /// 年度全量快照（`/pubmed/baseline/`）。
  BASELINE,

  /// 每日增量（`/pubmed/updatefiles/`）。
  UPDATE
}
