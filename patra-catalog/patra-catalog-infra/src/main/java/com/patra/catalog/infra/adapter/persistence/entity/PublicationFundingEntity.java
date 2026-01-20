package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 文献资助信息关联 JPA 实体，映射到表 `cat_publication_funding`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 资助机构通过 `organizationId` 关联到 `cat_organization`（type=FUNDER）
/// - 保留原始数据字段用于后续机构匹配和数据质量分析
///
/// **业务含义**：
///
/// 资助信息用于追踪研究资金来源，包括：
/// - 政府资助（NIH, NSF, NSFC 等）
/// - 基金会资助
/// - 企业资助
///
/// **索引设计**：
///
/// - `uk_pub_org_grant`：出版物+机构+项目编号唯一索引
/// - `idx_publication`：出版物索引
/// - `idx_organization`：机构索引
/// - `idx_provenance`：数据来源索引
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "cat_publication_funding",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pub_org_grant",
          columnNames = {"publication_id", "organization_id", "grant_id"})
    },
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_organization", columnList = "organization_id"),
      @Index(name = "idx_provenance", columnList = "provenance_code")
    })
public class PublicationFundingEntity extends ValueObjectJpaEntity {

  // ========== 关联关系 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 资助机构 ID（外键：cat_organization.id，匹配后填充）。
  ///
  /// 可能为 null，表示尚未匹配到机构。
  @Column(name = "organization_id")
  private Long organizationId;

  // ========== 资助项目信息 ==========

  /// 资助项目编号/授权号。
  @Column(name = "grant_id", length = 200)
  private String grantId;

  // ========== 原始数据保留 ==========

  /// 资助机构原始名称（来自数据源）。
  @Column(name = "funder_name_raw", length = 500)
  private String funderNameRaw;

  /// 资助机构缩写原始值（如 NIH, NSF, NSFC）。
  @Column(name = "funder_acronym_raw", length = 100)
  private String funderAcronymRaw;

  /// 资助机构标识符原始值（FundRef ID/ROR ID 等）。
  @Column(name = "funder_identifier_raw", length = 200)
  private String funderIdentifierRaw;

  /// 国家原始值。
  @Column(name = "country_raw", length = 100)
  private String countryRaw;

  // ========== 关联元数据 ==========

  /// 资助信息顺序（用于排序显示）。
  @Column(name = "funding_order", nullable = false)
  private Integer fundingOrder;

  // ========== 来源追踪 ==========

  /// 数据来源（PUBMED/OPENALEX/CROSSREF 等）。
  @Column(name = "provenance_code", nullable = false, length = 32)
  private String provenanceCode;
}
