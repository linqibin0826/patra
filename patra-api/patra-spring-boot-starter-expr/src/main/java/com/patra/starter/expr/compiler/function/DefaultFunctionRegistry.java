package com.patra.starter.expr.compiler.function;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link FunctionRegistry} 的默认不可变实现。在构造时使用函数列表初始化， 按代码索引以实现 O(1) 查找。通过不可变性保证线程安全。
 *
 * <p>参考：docs/expr/03-compiler-bridge-internals.md §3.7（线程安全性与性能）
 *
 * @since 1.0.0
 */
public class DefaultFunctionRegistry implements FunctionRegistry {

  private final Map<String, RenderFunction> functions;

  /**
   * 从函数列表构造注册表。函数按其代码索引以实现高效查找。
   *
   * @param functionList 要注册的渲染函数列表
   * @throws IllegalArgumentException 如果检测到重复的函数代码
   */
  public DefaultFunctionRegistry(List<RenderFunction> functionList) {
    if (functionList == null) {
      throw new IllegalArgumentException("函数列表不能为空");
    }

    this.functions =
        functionList.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    RenderFunction::code,
                    Function.identity(),
                    (f1, f2) -> {
                      throw new IllegalArgumentException("检测到重复的函数代码：" + f1.code());
                    }));
  }

  @Override
  public Optional<RenderFunction> find(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(functions.get(code));
  }
}
