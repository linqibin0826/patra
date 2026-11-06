package dev.patra.tool.core.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/// 聚合根的抽象基类。
///
/// 约束和约定:
///
/// - 仅依赖 JDK;领域层保持无框架依赖。
///   - 状态变更必须通过聚合行为发生以保持不变量。
///   - 基类不提供事件机制:聚合的行为方法返回事件对象(类型由各服务定义),应用层拿到后发布;
///     聚合不持有发布器,也不暂存事件。
///
/// @param <ID> 聚合标识符类型(值对象或封装的原始类型)
public abstract class AggregateRoot<ID> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 由仓储在首次持久化时分配的聚合标识符。
  ///
  /// 在聚合尚未持久化时,Getter 返回 `null`。
  @Getter private ID id;

  /// 由基础设施层维护的可选乐观锁版本。
  ///
  /// 主要用于领域内的只读检查。
  @Getter private long version;

  // ========== 子实体变更事件：追踪集合成员增删改 ==========

  /// 子实体变更事件列表。
  ///
  /// 记录聚合内部集合成员（子实体或值对象）的增删改操作，供 Repository 生成增量 SQL。
  private final transient List<ChildEntityChange> childChanges = new ArrayList<>();

  /// 默认构造函数,用于反序列化或仓储重建。
  protected AggregateRoot() {}

  /// 带标识符的构造函数,用于测试或工厂方法。
  ///
  /// @param id 聚合标识符
  protected AggregateRoot(ID id) {
    this.id = id;
  }

  /// 在重建或首次持久化时分配标识符。
  ///
  /// 仅供仓储使用。
  ///
  /// @param id 聚合标识符
  public void assignId(ID id) {
    this.id = Objects.requireNonNull(id, "聚合 ID 不能为 null");
  }

  /// 设置乐观锁版本。
  ///
  /// 基础设施层应在持久化更新时调用此方法。
  ///
  /// @param version 版本号,必须大于等于 0
  /// @throws IllegalArgumentException 如果版本号小于 0
  public void assignVersion(long version) {
    if (version < 0) {
      throw new IllegalArgumentException("版本必须 >= 0");
    }
    this.version = version;
  }

  /// 指示聚合是否尚未持久化。
  ///
  /// @return 如果聚合 ID 为 null 则返回 true,否则返回 false
  public boolean isTransient() {
    return this.id == null;
  }

  /// 领域不变量检查的钩子方法。
  ///
  /// 覆盖此方法以在关键转换后验证状态,并在不变量被违反时抛出 {@link IllegalStateException}。
  protected void assertInvariants() {
    // 默认为空操作;子类应强制执行不变量,如状态机有效性或值对象一致性。
  }

  // ========== 子实体变更追踪方法 ==========

  /// 记录子实体新增。
  ///
  /// @param type 子实体类型（子实体或值对象）
  /// @param entity 新增的子实体实例
  /// @param <E> 子实体类型参数
  protected <E> void trackChildAdded(Class<E> type, E entity) {
    childChanges.add(new ChildEntityChange.Added<>(type, entity));
  }

  /// 记录子实体更新。
  ///
  /// @param type 子实体类型（子实体或值对象）
  /// @param entity 更新后的子实体实例
  /// @param <E> 子实体类型参数
  protected <E> void trackChildUpdated(Class<E> type, E entity) {
    childChanges.add(new ChildEntityChange.Updated<>(type, entity));
  }

  /// 记录子实体删除。
  ///
  /// @param type 子实体类型（子实体或值对象）
  /// @param entityId 被删除子实体的标识（可以是 ID 或值对象实例本身）
  /// @param <E> 子实体类型参数
  protected <E> void trackChildRemoved(Class<E> type, Object entityId) {
    childChanges.add(new ChildEntityChange.Removed<>(type, entityId));
  }

  /// 提取并清空子实体变更事件。
  ///
  /// Repository 应在持久化时调用此方法获取所有变更。
  ///
  /// @return 子实体变更事件列表的副本，如果没有变更则返回空列表
  public List<ChildEntityChange> pullChildChanges() {
    if (childChanges.isEmpty()) {
      return List.of();
    }
    List<ChildEntityChange> snapshot = List.copyOf(childChanges);
    childChanges.clear();
    return snapshot;
  }

  /// 返回子实体变更事件的不可变视图（用于调试或测试）。
  ///
  /// @return 当前子实体变更事件的不可变列表视图
  public List<ChildEntityChange> peekChildChanges() {
    return Collections.unmodifiableList(childChanges);
  }

  /// 检查是否有未处理的子实体变更。
  ///
  /// @return 如果有子实体变更则返回 `true`
  public boolean hasChildChanges() {
    return !childChanges.isEmpty();
  }
}
