package com.patra.starter.expr.compiler.transform;

import java.util.Optional;

/// 参数级变换注册表，用于查找参数映射条目中 `transform_code` 引用的变换实现。
/// 
/// 实现必须是线程安全的，初始化后不可变。使用内部映射结构实现 O(1) 查找。
/// 
/// 参考：docs/expr/03-compiler-bridge-internals.md §3.3
/// 
/// @since 1.0.0
public interface TransformRegistry {

  /// 根据代码标识符查找值变换。
/// 
/// @param code 变换代码（例如 "TO_EXCLUSIVE_MINUS_1D", "LIST_JOIN"）
/// @return 如果找到变换则返回包含变换的 Optional，否则返回空
  Optional<ValueTransform> find(String code);
}
