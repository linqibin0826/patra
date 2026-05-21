package dev.linqibin.starter.jpa.entity;

/// JPA 实体 ID 访问接口。
///
/// 所有 JPA 实体基类（`BaseJpaEntity`、`ChildJpaEntity`、`ValueObjectJpaEntity`）
/// 都实现此接口，以支持统一的 ID 操作（如预分配雪花 ID）。
///
/// @author linqibin
/// @since 0.1.0
public interface IdAwareEntity {

  /// 获取实体 ID。
  ///
  /// @return 实体 ID，可能为 null（新实体）
  Long getId();

  /// 设置实体 ID。
  ///
  /// @param id 实体 ID
  void setId(Long id);
}
