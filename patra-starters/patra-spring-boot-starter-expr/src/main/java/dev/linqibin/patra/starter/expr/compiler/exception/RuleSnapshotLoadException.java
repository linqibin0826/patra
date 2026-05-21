package dev.linqibin.patra.starter.expr.compiler.exception;

import dev.linqibin.patra.common.enums.ProvenanceCode;

/// 规则快照加载异常
///
/// 当从 Registry 服务加载规则快照失败时抛出此异常。
///
/// @author linqibin
/// @since 0.1.0
public class RuleSnapshotLoadException extends RuntimeException {

  private final ProvenanceCode provenanceCode;

  /// 构造规则快照加载异常
  ///
  /// @param provenanceCode 数据源代码
  /// @param cause 原始异常
  public RuleSnapshotLoadException(ProvenanceCode provenanceCode, Throwable cause) {
    super("加载规则快照失败: provenanceCode=" + provenanceCode.getCode(), cause);
    this.provenanceCode = provenanceCode;
  }

  /// 获取数据源代码
  ///
  /// @return 数据源代码
  public ProvenanceCode getProvenanceCode() {
    return provenanceCode;
  }
}
