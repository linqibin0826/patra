package dev.linqibin.patra.starter.expr.compiler.transform;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// {@link TransformRegistry} 的默认不可变实现。在构造时使用变换列表初始化， 按代码索引以实现 O(1) 查找。通过不可变性保证线程安全。
///
/// 参考：docs/expr/03-compiler-bridge-internals.md §3.7（线程安全性与性能）
///
/// @since 0.1.0
public class DefaultTransformRegistry implements TransformRegistry {

  private final Map<String, ValueTransform> transforms;

  /// 从变换列表构造注册表。变换按其代码索引以实现高效查找。
  ///
  /// @param transformList 要注册的值变换列表
  /// @throws IllegalArgumentException 如果检测到重复的变换代码
  public DefaultTransformRegistry(List<ValueTransform> transformList) {
    if (transformList == null) {
      throw new IllegalArgumentException("变换列表不能为空");
    }

    this.transforms =
        transformList.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ValueTransform::code,
                    Function.identity(),
                    (t1, t2) -> {
                      throw new IllegalArgumentException("检测到重复的变换代码：" + t1.code());
                    }));
  }

  @Override
  public Optional<ValueTransform> find(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(transforms.get(code));
  }
}
