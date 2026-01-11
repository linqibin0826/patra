package com.patra.starter.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/// 支持软删除的 JPA 实体基类。
///
/// 继承此类的实体自动获得软删除能力：
///
/// - 通过 `@SQLRestriction` 自动过滤已删除记录
/// - 提供 `softDelete()` 和 `isDeleted()` 方法
///
/// **软删除实现**（时间戳策略）：
///
/// - 使用 `@SQLRestriction("deleted_at IS NULL")` 自动过滤已删除记录
/// - 应用层通过设置 `deletedAt = Instant.now()` 实现软删除
/// - 通过 `@SQLRestriction` 自动过滤，无需手动添加查询条件
/// - 需要查询已删除记录时，使用 Native Query 或自定义 Repository 方法
///
/// **为什么不用 @SoftDelete**：
///
/// Hibernate 7.1 的 `@SoftDelete` 设计为布尔型策略（`0/1`、`Y/N`），
/// 不原生支持时间戳策略。对于时间戳软删除，`@SQLRestriction` 是更好的选择。
///
/// 使用示例：
///
/// ```java
/// @Entity
/// @Table(name = "cat_venue")
/// public class VenueEntity extends SoftDeletableJpaEntity {
///     @Column(name = "title")
///     private String title;
/// }
///
/// // 软删除实体
/// entity.softDelete();
/// repository.save(entity);
///
/// // 查询自动排除已删除记录
/// repository.findAll(); // WHERE deleted_at IS NULL
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see BaseJpaEntity
/// @see SoftDeletable
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
@SQLRestriction("deleted_at IS NULL")
public abstract class SoftDeletableJpaEntity extends BaseJpaEntity implements SoftDeletable {

  /// 实体软删除时间戳。
  ///
  /// - `null`：记录未删除（活跃状态）
  /// - 非 `null`：记录已删除，值为删除时间
  ///
  /// 通过 `@SQLRestriction("deleted_at IS NULL")` 自动过滤已删除记录。
  /// 应用层执行软删除时，设置此字段为 `Instant.now()` 并调用 `save()`。
  @Column(name = "deleted_at")
  private Instant deletedAt;
}
