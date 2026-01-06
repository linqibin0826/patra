package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/// 机构关系 JPA 实体，映射到表 `cat_organization_relation`。
///
/// 存储机构之间的关系，如父子机构、继任/前任等。
///
/// **关系类型说明**：
///
/// | 类型 | 说明 |
/// |------|------|
/// | PARENT | 父机构（当前机构是其子机构） |
/// | CHILD | 子机构（当前机构是其父机构） |
/// | RELATED | 相关机构（非层级关系） |
/// | SUCCESSOR | 继任机构（当前机构已被合并/重组） |
/// | PREDECESSOR | 前任机构（当前机构由其演变而来） |
///
/// **延迟关联设计**：
///
/// - `related_ror_id`：始终存储，用于 ROR 数据导入时的关系建立
/// - `related_org_id`：导入完成后批量填充，用于内部查询优化
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "cat_organization_relation",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_org_relation",
          columnNames = {"org_id", "relation_type", "related_ror_id"})
    },
    indexes = {
      @Index(name = "idx_org_rel_org_id", columnList = "org_id"),
      @Index(name = "idx_org_rel_related_ror_id", columnList = "related_ror_id"),
      @Index(name = "idx_org_rel_related_org_id", columnList = "related_org_id")
    })
public class OrganizationRelationEntity extends BaseJpaEntity {

  /// 源机构 ID（逻辑外键）。
  @Column(name = "org_id", nullable = false)
  private Long orgId;

  /// 关系类型代码：PARENT/CHILD/RELATED/SUCCESSOR/PREDECESSOR。
  @Column(name = "relation_type", nullable = false, length = 20)
  private String relationType;

  /// 关联机构的 ROR ID。
  ///
  /// 用于 ROR 数据导入时的关系建立，始终存储。
  @Column(name = "related_ror_id", nullable = false, length = 50)
  private String relatedRorId;

  /// 关联机构的显示名称（冗余）。
  ///
  /// 存储关联机构的名称，避免查询时 JOIN。
  @Column(name = "related_label", length = 500)
  private String relatedLabel;

  /// 关联机构的内部 ID（延迟填充）。
  ///
  /// 在全量导入完成后，根据 `related_ror_id` 批量查找并填充。
  /// 可能为 null（如关联机构尚未导入）。
  @Column(name = "related_org_id")
  private Long relatedOrgId;
}
