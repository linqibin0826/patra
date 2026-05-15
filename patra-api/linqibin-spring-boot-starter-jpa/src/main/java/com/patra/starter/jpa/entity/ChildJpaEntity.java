package com.patra.starter.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/// 子实体 JPA 基类。
///
/// **适用场景**：
///
/// - 有独立更新语义的子实体（如任务执行记录、年度统计）
/// - 需要支持增量同步的实体（使用 createdAt/updatedAt 做变更检测）
/// - 可能存在并发更新的子表（需要乐观锁保护）
///
/// **字段说明**：
///
/// | 字段 | 说明 |
/// |------|------|
/// | id | 主键（雪花 ID） |
/// | version | 乐观锁版本号 |
/// | createdAt | 创建时间（用于增量同步） |
/// | updatedAt | 更新时间（用于增量同步） |
///
/// **与其他基类的区别**：
///
/// | 基类 | 字段 | 适用场景 |
/// |------|------|----------|
/// | BaseJpaEntity | 12 字段 | 聚合根，完整审计 |
/// | **ChildJpaEntity** | **4 字段** | **子实体，有独立更新** |
/// | ValueObjectJpaEntity | 1 字段 | 值对象表，DELETE/INSERT |
///
/// **与 BaseJpaEntity 的主要区别**：
///
/// - **无 createdBy/updatedBy**：子实体的操作人由聚合根追踪
/// - **无 recordRemarks/ipAddress**：减少冗余，审计日志在聚合根层面
///
/// **典型用例**：
///
/// - TaskRunEntity（任务执行记录）
/// - VenuePublicationStatsEntity（年度发文统计）
/// - AuthorNameVariantEntity（作者名字变体）
///
/// @author linqibin
/// @since 0.1.0
/// @see BaseJpaEntity
/// @see ValueObjectJpaEntity
/// @see SoftDeletableJpaEntity
/// @see SoftDeletableChildJpaEntity
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class ChildJpaEntity implements Serializable, IdAwareEntity {

  @Serial private static final long serialVersionUID = 1L;

  /// 实体的主键 ID。
  ///
  /// 使用应用层预分配的雪花 ID（`SnowflakeIdGenerator.getId()`）。
  /// 不使用 `@GeneratedValue`，因为数据库自增会破坏 JPA 批量插入优化。
  @Id
  @Column(name = "id")
  private Long id;

  /// 实体创建时间戳。
  ///
  /// 此字段在插入时由 Spring Data JPA Auditing 自动填充。
  /// 用于增量同步时的变更检测。
  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  /// 实体最后更新时间戳。
  ///
  /// 此字段在插入和更新时由 Spring Data JPA Auditing 自动填充。
  /// 用于增量同步时的变更检测。
  @LastModifiedDate
  @Column(name = "updated_at")
  private Instant updatedAt;

  /// 用于乐观锁的版本号。
  ///
  /// 防止并发更新冲突，由 JPA 自动管理。
  /// 初始值为 0，每次更新时自动递增。
  @Version
  @Column(name = "version")
  private Long version;
}
