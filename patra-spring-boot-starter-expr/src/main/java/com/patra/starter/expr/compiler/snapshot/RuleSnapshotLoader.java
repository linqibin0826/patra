package com.patra.starter.expr.compiler.snapshot;

import com.patra.common.enums.ProvenanceCode;

/// 规则快照加载器，用于加载 Provenance 配置快照。
public interface RuleSnapshotLoader {

  /// 加载指定 Provenance 的规则快照。
  ///
  /// @param provenanceCode Provenance 代码
  /// @param operationType 操作类型（例如 HARVEST、UPDATE）
  /// @param endpointName 端点名称（例如 SEARCH、DETAIL）
  /// @return Provenance 快照
  ProvenanceSnapshot load(ProvenanceCode provenanceCode, String operationType, String endpointName);
}
