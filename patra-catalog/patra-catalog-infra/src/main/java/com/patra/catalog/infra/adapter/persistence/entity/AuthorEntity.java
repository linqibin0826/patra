package com.patra.catalog.infra.adapter.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.catalog.infra.adapter.persistence.entity.embeddable.AuthorNameEmbeddable;
import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 作者 JPA 实体，映射到表 `cat_author`。
///
/// **设计说明**：
///
/// - 继承 `BaseJpaEntity` 获得审计、乐观锁、软删除功能
/// - 使用 `@Embedded` 嵌入 `AuthorNameEmbeddable` 值对象
/// - 使用 Hibernate 6.6 的 `@JdbcTypeCode(SqlTypes.JSON)` 处理 JSON 字段
///
/// **索引设计**：
///
/// - `uk_orcid`：ORCID 唯一索引（如果提供）
/// - `idx_email`：邮箱索引（用于查询）
/// - `idx_dedup_key`：去重键索引（用于查重）
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
    name = "cat_author",
    indexes = {
      @Index(name = "uk_orcid", columnList = "orcid", unique = true),
      @Index(name = "idx_email", columnList = "email"),
      @Index(name = "idx_dedup_key", columnList = "dedup_key")
    })
public class AuthorEntity extends BaseJpaEntity {

  // ========== 姓名信息（嵌入式值对象） ==========

  /// 作者姓名（嵌入式值对象）
  @Embedded private AuthorNameEmbeddable name;

  /// 机构名称（文本，不关联 Organization 表）
  @Column(name = "organization_name", length = 500)
  private String organizationName;

  // ========== 标识符 ==========

  /// ORCID 标识符（全局唯一，格式: 0000-0001-2345-6789）
  @Column(name = "orcid", length = 19)
  private String orcid;

  /// Researcher ID（ResearcherID/Publons）
  @Column(name = "researcher_id", length = 50)
  private String researcherId;

  /// Scopus 作者 ID
  @Column(name = "scopus_id", length = 50)
  private String scopusId;

  // ========== 联系方式 ==========

  /// 邮箱地址
  @Column(name = "email", length = 100)
  private String email;

  // ========== 去重和状态 ==========

  /// 复合去重键（MD5 哈希，应用层计算）
  @Column(name = "dedup_key", length = 32)
  private String dedupKey;

  /// 同等贡献标志（用于标记同等贡献作者）
  @Column(name = "equal_contribution")
  private Boolean equalContribution;

  /// 信息是否有效（false = 无效，如已合并的重复作者）
  @Column(name = "valid")
  private Boolean valid;

  // ========== 扩展字段 ==========

  /// 作者元数据（JSON 格式，灵活扩展）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "author_metadata", columnDefinition = "JSON")
  private JsonNode authorMetadata;
}
