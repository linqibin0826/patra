package com.patra.common.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
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
///   - 领域事件附加到聚合上,由应用层拉取后发布(例如,通过 outbox 或消息总线)。
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

  /// 待应用层收集的挂起领域事件。
  private final transient List<DomainEvent> domainEvents = new ArrayList<>();

  // ========== 脏标记：追踪聚合根字段更新 ==========

  /// 聚合根是否被修改。
  ///
  /// 在业务方法中修改字段后调用 `markDirty()`，Repository 据此决定是否执行 UPDATE。
  private transient boolean dirty = false;

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

  /// 注册由聚合行为在状态变更后产生的领域事件。
  ///
  /// @param event 领域事件,如果为 null 则忽略
  protected void addDomainEvent(DomainEvent event) {
    if (event == null) return;
    domainEvents.add(event);
  }

  /// 提取并清空暂存的领域事件。
  ///
  /// 应用层应在事务边界内调用此方法,然后再发布到 outbox。
  ///
  /// @return 已注册的领域事件列表的不可变副本,如果没有事件则返回空列表
  public List<DomainEvent> pullDomainEvents() {
    if (domainEvents.isEmpty()) {
      return Collections.emptyList();
    }
    List<DomainEvent> snapshot = List.copyOf(domainEvents);
    domainEvents.clear();
    return snapshot;
  }

  /// 返回暂存领域事件的不可变视图(用于调试或测试)。
  ///
  /// @return 当前已注册的领域事件的不可变列表视图
  public List<DomainEvent> peekDomainEvents() {
    return Collections.unmodifiableList(domainEvents);
  }

  /// 领域不变量检查的钩子方法。
  ///
  /// 覆盖此方法以在关键转换后验证状态,并在不变量被违反时抛出 {@link IllegalStateException}。
  protected void assertInvariants() {
    // 默认为空操作;子类应强制执行不变量,如状态机有效性或值对象一致性。
  }

  /* ========== 为事件分配默认时间戳的可选辅助方法 ========== */

  /// 当事件时间戳缺失时提供当前时间。
  ///
  /// @param t 时间戳,可能为 null
  /// @return 如果 t 为 null 则返回当前时间,否则返回 t
  protected static Instant nowIfNull(Instant t) {
    return (t == null) ? Instant.now() : t;
  }

  // ========== 脏标记方法 ==========

  /// 标记聚合根已被修改。
  ///
  /// 在任何修改字段的业务方法中调用此方法。多次调用不会产生额外开销。
  protected void markDirty() {
    this.dirty = true;
  }

  /// 检查聚合根是否被修改。
  ///
  /// @return 如果调用过 `markDirty()` 则返回 `true`
  public boolean isDirty() {
    return dirty;
  }

  /// 清除脏标记。
  ///
  /// Repository 持久化成功后调用。
  public void clearDirty() {
    this.dirty = false;
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
      return Collections.emptyList();
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
