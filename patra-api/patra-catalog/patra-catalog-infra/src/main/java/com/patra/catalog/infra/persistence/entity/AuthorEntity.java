package com.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 作者 JPA 实体，映射到表 `cat_author`。
///
/// **设计说明**：
///
/// - 继承 `SoftDeletableJpaEntity` 支持软删除
/// - 每条记录代表一个已消歧的独立作者（对齐 PubMed Computed Authors 数据源）
/// - `normalizedKey` 是姓名规范化格式（如 "SMITH+R"），用于分组查询，**非唯一标识**
/// - 同一 `normalizedKey` 下可能有多个不同的作者（姓名格式相似但实为不同人）
/// - 关联名字变体（`AuthorNameVariantEntity`）和 ORCID（`AuthorOrcidEntity`）子表
///
/// **索引设计**：
///
/// - `idx_normalized_key`：姓名规范化格式索引（非唯一，用于分组查询）
/// - `idx_status`：状态索引
/// - `idx_provenance`：数据来源索引
/// - `idx_display_name`：展示名称前缀索引
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
      @Index(name = "idx_normalized_key", columnList = "normalized_key"),
      @Index(name = "idx_status", columnList = "status"),
      @Index(name = "idx_provenance", columnList = "provenance_code"),
      @Index(name = "idx_display_name", columnList = "display_name")
    })
public class AuthorEntity extends SoftDeletableJpaEntity {

  // ========== 核心属性 ==========

  /// 姓名规范化格式，对应 PubMed Computed Authors 的 name 字段。
  ///
  /// 格式如 "SMITH+R"（姓 Smith + 首字母 R），用于分组查询。
  /// **非唯一标识**：同一格式下可能有多个不同的已消歧作者。
  @Column(name = "normalized_key", length = 100, nullable = false)
  private String normalizedKey;

  /// 展示名称（从首个名字变体派生）。
  ///
  /// 格式如 "Zhiyong Lu"，用于界面显示和搜索。
  @Column(name = "display_name", length = 200)
  private String displayName;

  /// 作者状态。
  ///
  /// ACTIVE：活跃状态（正常使用）
  /// MERGED：已合并状态（合并到其他作者）
  /// INACTIVE：已停用状态（标记为无效）
  @Column(name = "status", length = 20, nullable = false)
  private String status;

  // ========== 来源追踪 ==========

  /// 数据来源代码。
  ///
  /// 标识作者数据最初来源于哪个系统：PUBMED、ORCID、OPENALEX、MANUAL 等。
  @Column(name = "provenance_code", length = 32, nullable = false)
  private String provenanceCode;

  /// 最后同步时间（UTC，微秒精度）。
  ///
  /// 记录上次与外部数据源同步的时间。
  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  // ========== 扩展字段 ==========

  /// 扩展数据（JSON 格式，预留 ORCID API 补充信息）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ext_data", columnDefinition = "JSON")
  private JsonNode extData;

  // ========== 关联子实体 ==========

  /// 名字变体集合。
  ///
  /// 存储作者在不同文献中出现的各种名字形式，
  /// 解析自 PubMed Computed Authors 的 names 数组。
  ///
  /// **使用 Set 而非 List 的原因**：
  ///
  /// - 语义正确：名字变体是唯一的，不允许重复
  /// - JPA 优化：Hibernate 支持多 Set 同时 JOIN FETCH，避免 N+1 问题
  @OneToMany(
      mappedBy = "author",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @lombok.Builder.Default
  private Set<AuthorNameVariantEntity> nameVariants = new HashSet<>();

  /// ORCID 标识符集合。
  ///
  /// 支持一对多关系（少数作者有多个 ORCID）。
  ///
  /// **使用 Set 而非 List 的原因**：同上（语义正确 + JPA 优化）。
  @OneToMany(
      mappedBy = "author",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @lombok.Builder.Default
  private Set<AuthorOrcidEntity> orcids = new HashSet<>();

  // ========== 便捷方法 ==========

  /// 添加名字变体并建立双向关联。
  ///
  /// @param variant 名字变体实体
  public void addNameVariant(AuthorNameVariantEntity variant) {
    nameVariants.add(variant);
    variant.setAuthor(this);
  }

  /// 移除名字变体并解除双向关联。
  ///
  /// @param variant 名字变体实体
  public void removeNameVariant(AuthorNameVariantEntity variant) {
    nameVariants.remove(variant);
    variant.setAuthor(null);
  }

  /// 添加 ORCID 并建立双向关联。
  ///
  /// @param orcid ORCID 实体
  public void addOrcid(AuthorOrcidEntity orcid) {
    orcids.add(orcid);
    orcid.setAuthor(this);
  }

  /// 移除 ORCID 并解除双向关联。
  ///
  /// @param orcid ORCID 实体
  public void removeOrcid(AuthorOrcidEntity orcid) {
    orcids.remove(orcid);
    orcid.setAuthor(null);
  }
}
