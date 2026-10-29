package dev.patra.ingest.expr.compiler.snapshot;

import dev.patra.common.ProvenanceCode;
import dev.patra.ingest.expr.compiler.exception.RuleSnapshotLoadException;
import dev.patra.ingest.expr.compiler.snapshot.convert.SnapshotAssembler;
import dev.patra.registry.api.expr.dto.ExprSnapshotResp;
import dev.patra.registry.api.expr.endpoint.ExprEndpoint;
import dev.patra.registry.api.provenance.dto.ProvenanceResp;
import dev.patra.registry.api.provenance.endpoint.ProvenanceEndpoint;
import dev.patra.tool.core.error.remote.RemoteCallException;
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
