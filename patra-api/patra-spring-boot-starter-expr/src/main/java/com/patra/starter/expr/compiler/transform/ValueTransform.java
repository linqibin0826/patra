package com.patra.starter.expr.compiler.transform;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

/// 参数级转换,在 std_key → 提供商参数映射之后应用。转换操作最终映射的值(提供商特定语义)。
///
/// 执行发生在编译器阶段,在所有 std_keys 已映射到提供商参数名称之后。转换通过其代码标识, 并通过参数映射条目中的 `transform_code` 应用。
///
/// 示例:`TO_EXCLUSIVE_MINUS_1D` 从日期值中减去一天,将排他的上界转换为提供商的包含边界。
///
/// 参见: docs/expr/03-compiler-bridge-internals.md §3.3.1
///
/// @author linqibin
/// @since 0.1.0
public interface ValueTransform {

  /// 返回唯一的转换代码标识符。此代码在参数映射的 `transform_code` 字段中引用。
  ///
  /// @return 转换代码(例如 "TO_EXCLUSIVE_MINUS_1D")
  String code();

  /// 对单个映射的 std_key 值应用转换逻辑。返回适合提供商参数的转换值。
  ///
  /// 快照提供对溯源配置的访问,用于上下文感知的转换。
  ///
  /// @param stdKey 正在转换的 std_key(例如 "to"、"query"、"filter")
  /// @param value 转换前的映射值(例如 "2023-12-31"、"cancer AND therapy")
  /// @param snapshot 用于上下文感知转换逻辑的溯源快照
  /// @return 转换后的值(例如减去一天后的 "2023-12-30")
  String apply(String stdKey, String value, ProvenanceSnapshot snapshot);
}
