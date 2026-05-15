package com.patra.starter.expr.compiler.snapshot;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.dto.expr.ExprSnapshotResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import com.patra.registry.api.endpoint.ExprEndpoint;
import com.patra.registry.api.endpoint.ProvenanceEndpoint;
import com.patra.starter.expr.compiler.exception.RuleSnapshotLoadException;
import com.patra.starter.expr.compiler.snapshot.convert.SnapshotAssembler;
import dev.linqibin.commons.error.remote.RemoteCallException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/// 基于 Registry 的快照加载器，将 DTO→领域对象转换委托给专用组装器。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class RegistryRuleSnapshotLoader implements RuleSnapshotLoader {

  private final ProvenanceEndpoint provenanceEndpoint;
  private final ExprEndpoint exprEndpoint;
  private final SnapshotAssembler snapshotAssembler;

  /// 构造 Registry 快照加载器。
  ///
  /// @param provenanceEndpoint 数据源端点（必需，非空）
  /// @param exprEndpoint 表达式端点（必需，非空）
  /// @param snapshotAssembler 快照组装器（必需，非空）
  public RegistryRuleSnapshotLoader(
      ProvenanceEndpoint provenanceEndpoint,
      ExprEndpoint exprEndpoint,
      SnapshotAssembler snapshotAssembler) {
    this.provenanceEndpoint = Objects.requireNonNull(provenanceEndpoint);
    this.exprEndpoint = Objects.requireNonNull(exprEndpoint);
    this.snapshotAssembler = Objects.requireNonNull(snapshotAssembler);
  }

  @Override
  public ProvenanceSnapshot load(
      ProvenanceCode provenanceCode, String operationType, String endpointName) {
    try {
      ProvenanceResp provenance = provenanceEndpoint.getProvenance(provenanceCode);
      ExprSnapshotResp snapshot =
          exprEndpoint.getSnapshot(provenanceCode.getCode(), operationType, endpointName, null);
      return snapshotAssembler.assemble(provenance, snapshot, operationType, endpointName);
    } catch (RemoteCallException ex) {
      log.error(
          "加载规则快照失败: provenanceCode={} operationType={} endpointName={} httpStatus={}",
          provenanceCode,
          operationType,
          endpointName,
          ex.getHttpStatus(),
          ex);
      throw new RuleSnapshotLoadException(provenanceCode, ex);
    }
  }
}
