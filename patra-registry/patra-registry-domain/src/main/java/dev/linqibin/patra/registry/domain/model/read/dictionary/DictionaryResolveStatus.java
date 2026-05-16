package dev.linqibin.patra.registry.domain.model.read.dictionary;

/// 字典解析状态。
///
/// 描述解析结果的基本语义，用于上游系统处理未知或禁用值。
///
/// @author linqibin
/// @since 0.1.0
public enum DictionaryResolveStatus {
  /// 已成功解析为规范字典项。
  RESOLVED,
  /// 无法解析，暂时未知。
  UNKNOWN,
  /// 命中但字典项已禁用。
  DISABLED,
  /// 命中多个候选，无法唯一确定。
  AMBIGUOUS
}
