package com.patra.starter.expr.compiler.function;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.Map;

/// 渲染时函数,为 PARAMS 渲染派生或变更占位符值。函数在 std_key/placeholder 空间中操作(与提供商无关)。
///
/// 执行发生在渲染器阶段,在模板展开之前。函数通过其代码标识,并通过渲染规则中的 `fn_code` 应用。
///
/// 示例:`PUBMED_DATETYPE` 根据上下文返回 "pdat" 或 "edat"。
///
/// 参见: docs/expr/03-compiler-bridge-internals.md §3.3.1
///
/// @author linqibin
/// @since 0.1.0
public interface RenderFunction {

  /// 返回唯一的函数代码标识符。此代码在渲染规则的 `fn_code` 字段中引用。
  ///
  /// @return 函数代码(例如 "PUBMED_DATETYPE")
  String code();

  /// 应用函数逻辑以派生或变更占位符值。函数可以读取或修改占位符映射。
  ///
  /// 快照提供对溯源配置的访问,用于上下文感知的函数逻辑。
  ///
  /// @param placeholders 占位符名称到值的可变映射(例如 {"from": "2023-01-01", "to": "2023-12-31"})
  /// @param snapshot 用于上下文感知函数逻辑的溯源快照
  /// @return 派生值或修改后的占位符值(通常是单个占位符值)
  String apply(Map<String, String> placeholders, ProvenanceSnapshot snapshot);
}
