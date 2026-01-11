package com.patra.starter.jpa.entity;

import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

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
/// | deleted_at | 软删除时间戳（由 @SoftDelete 管理，非实体字段） |
///
/// **基类体系对比**：
///
/// | 基类 | 字段 | 适用场景 |
/// |------|------|----------|
/// | BaseJpaEntity | 10 字段 | 聚合根，完整审计 |
/// | SoftDeletableJpaEntity | 10 字段 + 软删除 | 需要软删除的聚合根 |
/// | ChildJpaEntity | 4 字段 | 子实体，有独立更新 |
/// | **SoftDeletableChildJpaEntity** | **4 字段 + 软删除** | **需要软删除的子实体** |
/// | ValueObjectJpaEntity | 1 字段 | 值对象表，DELETE/INSERT |
///
/// **软删除实现**（Hibernate 原生 @SoftDelete）：
///
/// - 使用 `@SoftDelete(strategy = SoftDeleteType.TIMESTAMP)` 注解
/// - Hibernate 自动将 `DELETE` 语句转换为 `UPDATE deleted_at = CURRENT_TIMESTAMP`
/// - 所有查询自动添加 `WHERE deleted_at IS NULL` 条件
///
/// **与 ChildJpaEntity 的区别**：
///
/// - 通过 `@SoftDelete` 注解支持软删除
/// - 删除操作转换为更新 `deleted_at` 字段
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
/// @see SoftDeletableJpaEntity
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public abstract class SoftDeletableChildJpaEntity extends ChildJpaEntity {

  @Serial private static final long serialVersionUID = 1L;
}
