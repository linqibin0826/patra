package com.patra.starter.jpa.entity;

import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

/// 支持软删除的 JPA 实体基类。
///
/// 继承此类的实体自动获得软删除能力：
///
/// - 调用 `repository.delete(entity)` 或 `entityManager.remove(entity)` 时自动执行软删除
/// - 所有查询自动过滤已删除记录
/// - 使用时间戳策略记录删除时间，便于审计
///
/// **软删除实现**（Hibernate 原生 @SoftDelete）：
///
/// - 使用 `@SoftDelete(strategy = SoftDeleteType.TIMESTAMP)` 注解
/// - Hibernate 自动将 `DELETE` 语句转换为 `UPDATE deleted_at = CURRENT_TIMESTAMP`
/// - 所有查询自动添加 `WHERE deleted_at IS NULL` 条件
/// - 列名使用 `deleted_at`，与数据库现有字段兼容
///
/// **删除操作**：
///
/// ```java
/// // 软删除实体（框架自动转换为 UPDATE）
/// repository.delete(entity);
/// // 或
/// entityManager.remove(entity);
/// ```
///
/// **查询已删除记录**：
///
/// 需要查询已删除记录时（如审计场景），使用 Native Query 绕过过滤：
///
/// ```java
/// @Query(value = "SELECT * FROM cat_venue WHERE deleted_at IS NOT NULL", nativeQuery = true)
/// List<VenueEntity> findDeleted();
/// ```
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
/// repository.delete(entity);
///
/// // 查询自动排除已删除记录
/// repository.findAll(); // WHERE deleted_at IS NULL
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see BaseJpaEntity
/// @see SoftDeletableChildJpaEntity
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public abstract class SoftDeletableJpaEntity extends BaseJpaEntity {}
