package com.patra.catalog.infra.adapter.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
/// - 继承 `SoftDeletableJpaEntity` 支持软删除
/// - 适配 PubMed Computed Authors 数据源
/// - 使用 `normalizedKey` 作为业务键（如 "Lu+Z"）
/// - 关联名字变体（`AuthorNameVariantEntity`）和 ORCID（`AuthorOrcidEntity`）子表
///
/// **索引设计**：
///
/// - `uk_normalized_key`：规范化标识唯一索引
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
      @Index(name = "uk_normalized_key", columnList = "normalized_key", unique = true),
      @Index(name = "idx_status", columnList = "status"),
      @Index(name = "idx_provenance", columnList = "provenance_code"),
      @Index(name = "idx_display_name", columnList = "display_name")
    })
public class AuthorEntity extends SoftDeletableJpaEntity {

  // ========== 核心属性 ==========

  /// 规范化标识（业务键），与 PubMed Computed Authors 的 name 字段对齐。
  ///
  /// 格式如 "Lu+Z"、"Smith+JK"，用于去重和外部数据源对齐。
  @Column(name = "normalized_key", length = 100, nullable = false, unique = true)
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
  @OneToMany(
      mappedBy = "author",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @lombok.Builder.Default
  private List<AuthorNameVariantEntity> nameVariants = new ArrayList<>();

  /// ORCID 标识符集合。
  ///
  /// 支持一对多关系（少数作者有多个 ORCID）。
  @OneToMany(
      mappedBy = "author",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @lombok.Builder.Default
  private List<AuthorOrcidEntity> orcids = new ArrayList<>();

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
