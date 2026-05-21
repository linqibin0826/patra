package dev.linqibin.starter.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 值对象表 JPA 基类。
///
/// **适用场景**：
///
/// - 采用 DELETE/INSERT 模式更新的表（如标识符、树形编号）
/// - 随聚合根整体替换的关联数据
/// - 不需要单独追踪变更历史的表
///
/// **设计原则**：
///
/// - **无 version**：DELETE/INSERT 模式无并发更新冲突风险
/// - **无时间戳**：生命周期完全由聚合根管理
/// - **无审计信息**：操作人信息在聚合根层面追踪
///
/// **与其他基类的区别**：
///
/// | 基类 | 字段 | 适用场景 |
/// |------|------|----------|
/// | BaseJpaEntity | 12 字段 | 聚合根，完整审计 |
/// | ChildJpaEntity | 4 字段 | 子实体，有独立更新 |
/// | **ValueObjectJpaEntity** | **1 字段** | **值对象表，DELETE/INSERT** |
///
/// **使用模式**：
///
/// ```java
/// // 聚合根保存时的典型操作（全删全增）
/// venueIdentifierRepository.deleteByVenueId(venue.getId());
/// venueIdentifierRepository.saveAll(newIdentifiers);
/// ```
///
/// **典型用例**：
///
/// - VenueIdentifierEntity（载体标识符）
/// - MeshTreeNumberEntity（MeSH 树形编号）
/// - OrganizationNameEntity（机构名称）
/// - PublicationAuthorEntity（文献-作者关联表）
///
/// @author linqibin
/// @since 0.1.0
/// @see BaseJpaEntity
/// @see ChildJpaEntity
/// @see SoftDeletableJpaEntity
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@MappedSuperclass
public abstract class ValueObjectJpaEntity implements Serializable, IdAwareEntity {

  @Serial private static final long serialVersionUID = 1L;

  /// 实体的主键 ID。
  ///
  /// 使用应用层预分配的雪花 ID（`SnowflakeIdGenerator.getId()`）。
  /// 不使用 `@GeneratedValue`，因为数据库自增会破坏 JPA 批量插入优化。
  @Id
  @Column(name = "id")
  private Long id;
}
