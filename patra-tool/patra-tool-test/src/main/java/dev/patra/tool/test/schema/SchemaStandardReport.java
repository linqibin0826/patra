package dev.patra.tool.test.schema;

import java.util.List;

/// 一次结构检查的结果，没有违规时列表为空。
///
/// @param violations 全部违规
public record SchemaStandardReport(List<SchemaViolation> violations) {

  /// 防御性拷贝。
  public SchemaStandardReport {
    violations = violations != null ? List.copyOf(violations) : List.of();
  }
}
