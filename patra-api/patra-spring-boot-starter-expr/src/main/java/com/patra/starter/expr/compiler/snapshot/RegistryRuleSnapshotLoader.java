package com.patra.starter.expr.compiler.snapshot;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.client.ExprClient;
import com.patra.registry.api.client.ProvenanceClient;
import com.patra.registry.api.dto.expr.ExprSnapshotResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import com.patra.starter.expr.compiler.snapshot.convert.SnapshotAssembler;
import java.util.Objects;

/// 基于 Registry 的快照加载器，将 DTO→领域对象转换委托给专用组装器。
///
/// @author linqibin
/// @since 0.1.0
public class RegistryRuleSnapshotLoader implements RuleSnapshotLoader {

  private final ProvenanceClient provenanceClient;
  private final ExprClient exprClient;
  private final SnapshotAssembler snapshotAssembler;

  /// 构造 Registry 快照加载器。
  ///
  /// @param provenanceClient 数据源客户端（必需，非空）
  /// @param exprClient 表达式客户端（必需，非空）
  /// @param snapshotAssembler 快照组装器（必需，非空）
  public RegistryRuleSnapshotLoader(
      ProvenanceClient provenanceClient,
      ExprClient exprClient,
      SnapshotAssembler snapshotAssembler) {
    this.provenanceClient = Objects.requireNonNull(provenanceClient);
    this.exprClient = Objects.requireNonNull(exprClient);
    this.snapshotAssembler = Objects.requireNonNull(snapshotAssembler);
  }

  @Override
  public ProvenanceSnapshot load(
      ProvenanceCode provenanceCode, String operationType, String endpointName) {
    ProvenanceResp provenance = provenanceClient.getProvenance(provenanceCode);
    ExprSnapshotResp snapshot =
        exprClient.getSnapshot(provenanceCode.getCode(), operationType, endpointName, null);
    return snapshotAssembler.assemble(provenance, snapshot, operationType, endpointName);
  }
}
