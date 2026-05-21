package dev.linqibin.patra.starter.expr.compiler.function;

import java.util.Optional;

/// 渲染时函数注册表，用于查找渲染规则中 `fn_code` 引用的函数实现。
///
/// 实现必须是线程安全的，初始化后不可变。使用内部映射结构实现 O(1) 查找。
///
/// 参考：docs/expr/03-compiler-bridge-internals.md §3.3
///
/// @since 0.1.0
public interface FunctionRegistry {

  /// 根据代码标识符查找渲染函数。
  ///
  /// @param code 函数代码（例如 "PUBMED_DATETYPE"）
  /// @return 如果找到函数则返回包含函数的 Optional，否则返回空
  Optional<RenderFunction> find(String code);
}
