package dev.linqibin.commons.domain;

import java.io.Serial;
import java.io.Serializable;

/// 子实体变更事件。
///
/// 记录聚合内部子实体集合的增删改操作，供 Repository 生成增量 SQL。
/// 这是一个 sealed interface，只允许三种实现：
///
/// - `Added` — 子实体新增
/// - `Updated` — 子实体更新
/// - `Removed` — 子实体删除
///
/// 使用示例：
/// ```java
/// // 在聚合根中追踪子实体变更
/// public void addItem(OrderItem item) {
///     items.add(item);
///     trackChildAdded(OrderItem.class, item);
/// }
///
/// // 在 Repository 中处理变更
/// List<ChildEntityChange> changes = aggregate.pullChildChanges();
/// for (ChildEntityChange change : changes) {
///     switch (change) {
///         case Added(var type, var entity) -> insertChild(entity);
///         case Updated(var type, var entity) -> updateChild(entity);
///         case Removed(var type, var id) -> deleteChild(type, id);
///     }
/// }
/// ```
public sealed interface ChildEntityChange extends Serializable {

  /// 获取变更的实体类型。
  ///
  /// @return 子实体的 Class 对象
  Class<?> entityType();

  /// 子实体新增事件。
  ///
  /// @param entityType 子实体类型
  /// @param entity 新增的子实体实例
  /// @param <E> 子实体类型参数
  record Added<E>(Class<E> entityType, E entity) implements ChildEntityChange {
    @Serial private static final long serialVersionUID = 1L;
  }

  /// 子实体更新事件。
  ///
  /// @param entityType 子实体类型
  /// @param entity 更新后的子实体实例
  /// @param <E> 子实体类型参数
  record Updated<E>(Class<E> entityType, E entity) implements ChildEntityChange {
    @Serial private static final long serialVersionUID = 1L;
  }

  /// 子实体删除事件。
  ///
  /// @param entityType 子实体类型
  /// @param entityId 被删除实体的 ID
  /// @param <E> 子实体类型参数
  record Removed<E>(Class<E> entityType, Object entityId) implements ChildEntityChange {
    @Serial private static final long serialVersionUID = 1L;
  }
}
