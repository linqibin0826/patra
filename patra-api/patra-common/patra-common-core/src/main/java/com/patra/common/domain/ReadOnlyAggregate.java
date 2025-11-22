package com.patra.common.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

/// 只读聚合的抽象基类。
///
/// 专为 CQRS 查询端设计,管理标识符而不引入写端关注点,如领域事件或版本控制。
///
/// 约束:
///
/// - 仅依赖 JDK;领域层保持无框架依赖。
///   - 专注于数据检索和业务规则验证。
///   - 不支持状态变更或事件发布。
///   - 非常适合配置、字典和视图类型的聚合。
///
/// @param <ID> 聚合标识符类型(值对象或封装的原始类型)
public abstract class ReadOnlyAggregate<ID> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 聚合标识符。
  @Getter private final ID id;

  protected ReadOnlyAggregate(ID id) {
    this.id = Objects.requireNonNull(id, "聚合 ID 不能为 null");
  }

  protected ReadOnlyAggregate() {
    this.id = null;
  }

  /// 指示聚合是否为瞬态(标识符未分配)。
  ///
  /// @return 如果聚合 ID 为 null 则返回 true,否则返回 false
  public boolean isTransient() {
    return this.id == null;
  }

  /// 不变量检查的钩子方法。
  ///
  /// 覆盖此方法以在构造或查询时操作期间验证状态,并在不变量不成立时抛出 {@link IllegalStateException}。
  protected void assertInvariants() {
    // 默认为空操作;子类应强制执行数据完整性和业务规则。
  }

  /// 基于聚合标识符的相等性。
  ///
  /// @param o 要比较的对象
  /// @return 如果两个聚合的 ID 相同则返回 true
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReadOnlyAggregate<?> that = (ReadOnlyAggregate<?>) o;
    return Objects.equals(id, that.id);
  }

  /// 从聚合标识符派生的哈希码。
  ///
  /// @return 基于聚合 ID 的哈希码
  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  /// 包含标识符的可读表示形式。
  ///
  /// @return 格式为 "类名{id=标识符}" 的字符串
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + "id=" + id + '}';
  }
}
