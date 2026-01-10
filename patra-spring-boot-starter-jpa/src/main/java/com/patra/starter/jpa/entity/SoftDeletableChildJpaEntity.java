package com.patra.starter.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/// 支持软删除的子实体 JPA 基类。
///
/// **适用场景**：
///
/// - 有独立更新语义且需要软删除的子实体
/// - 需要保留历史记录的子表
/// - 子表数据需要逻辑删除而非物理删除
///
/// **字段说明**：
///
/// | 字段 | 说明 |
/// |------|------|
/// | id | 主键（雪花 ID） |
/// | version | 乐观锁版本号 |
/// | createdAt | 创建时间（用于增量同步） |
/// | updatedAt | 更新时间（用于增量同步） |
/// | deletedAt | 软删除时间戳 |
///
/// **基类体系对比**：
///
/// | 基类 | 字段 | 适用场景 |
/// |------|------|----------|
/// | BaseJpaEntity | 12 字段 | 聚合根，完整审计 |
/// | SoftDeletableJpaEntity | 13 字段 | 需要软删除的聚合根 |
/// | ChildJpaEntity | 4 字段 | 子实体，有独立更新 |
/// | **SoftDeletableChildJpaEntity** | **5 字段** | **需要软删除的子实体** |
/// | ValueObjectJpaEntity | 1 字段 | 值对象表，DELETE/INSERT |
///
/// **与 ChildJpaEntity 的区别**：
///
/// - 增加 `deletedAt` 字段支持软删除
/// - 自动添加 `@SQLRestriction` 过滤已删除记录
///
/// **与 SoftDeletableJpaEntity 的区别**：
///
/// - **无 createdBy/updatedBy**：子实体的操作人由聚合根追踪
/// - **无 recordRemarks/ipAddress**：减少冗余，审计日志在聚合根层面
///
/// **典型用例**：
///
/// - 需要保留删除历史的任务执行记录
/// - 需要软删除的子表数据
///
/// @author linqibin
/// @since 0.1.0
/// @see ChildJpaEntity
/// @see SoftDeletable
/// @see SoftDeletableJpaEntity
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
@SQLRestriction("deleted_at IS NULL")
public abstract class SoftDeletableChildJpaEntity extends ChildJpaEntity implements SoftDeletable {

  @Serial private static final long serialVersionUID = 1L;

  /// 软删除时间戳。
  ///
  /// 当实体被逻辑删除时，此字段记录删除时间。
  /// 配合 `@SQLRestriction("deleted_at IS NULL")` 自动过滤已删除记录。
  /// 使用 `softDelete()` 方法设置此字段。
  @Column(name = "deleted_at")
  private Instant deletedAt;
}
