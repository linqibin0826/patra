package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.catalog.domain.model.enums.DisambiguationMethod;
import com.patra.catalog.domain.model.enums.DisambiguationStatus;
import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 作者-机构归属 JPA 实体，映射到表 `cat_publication_author_affiliation`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 支持一个作者在一篇文献中的多机构归属
/// - 冗余 `publication_id` 和 `author_id` 避免 JOIN `cat_publication_author`
/// - 存储机构标识符（ROR/Ringgold/GRID）用于后续消歧
/// - 消歧结果延迟填充，支持批量处理
///
/// **索引设计**：
///
/// - `uk_pub_author_order`：作者-文献关联 + 机构顺序唯一索引
/// - `idx_pub_author`：作者-文献关联索引
/// - `idx_publication`：出版物索引
/// - `idx_author`：作者索引
/// - `idx_organization`：机构索引
/// - `idx_ror_id`：ROR ID 索引（加速消歧）
/// - `idx_ringgold_id`：Ringgold ID 索引（加速消歧）
/// - `idx_disambiguation_status`：消歧状态索引（批量处理）
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
    name = "cat_publication_author_affiliation",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pub_author_order",
          columnNames = {"pub_author_id", "affiliation_order"})
    },
    indexes = {
      @Index(name = "idx_pub_author", columnList = "pub_author_id"),
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_author", columnList = "author_id"),
      @Index(name = "idx_organization", columnList = "organization_id"),
      @Index(name = "idx_ror_id", columnList = "ror_id"),
      @Index(name = "idx_ringgold_id", columnList = "ringgold_id"),
      @Index(name = "idx_disambiguation_status", columnList = "disambiguation_status")
    })
public class PublicationAuthorAffiliationEntity extends ValueObjectJpaEntity {

  // ========== 关联信息（冗余 publication_id/author_id 避免 JOIN） ==========

  /// 文献-作者关联 ID（外键：cat_publication_author.id）。
  @Column(name = "pub_author_id", nullable = false)
  private Long pubAuthorId;

  /// 出版物 ID（冗余，避免 JOIN）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 作者 ID（冗余，避免 JOIN）。
  @Column(name = "author_id", nullable = false)
  private Long authorId;

  // ========== 原始机构信息 ==========

  /// 机构顺序（1=第一机构，2=第二机构...）。
  @Column(name = "affiliation_order", nullable = false)
  @lombok.Builder.Default
  private Integer affiliationOrder = 1;

  /// 原始机构字符串（外部采集，未标准化）。
  @Column(name = "affiliation_string", nullable = false, length = 2000)
  private String affiliationString;

  // ========== 机构标识符（用于消歧，PubMed 可能提供） ==========

  /// ROR ID（如 "03vek6s52"）。
  @Column(name = "ror_id", length = 50)
  private String rorId;

  /// Ringgold ID。
  @Column(name = "ringgold_id", length = 20)
  private String ringgoldId;

  /// GRID ID（历史数据，已废弃）。
  @Column(name = "grid_id", length = 50)
  private String gridId;

  // ========== 消歧结果（延迟填充） ==========

  /// 关联机构 ID（外键：cat_organization.id，延迟填充）。
  @Column(name = "organization_id")
  private Long organizationId;

  /// 消歧状态（PENDING/MATCHED/UNMATCHED/AMBIGUOUS）。
  @Enumerated(EnumType.STRING)
  @Column(name = "disambiguation_status", nullable = false, length = 20)
  @lombok.Builder.Default
  private DisambiguationStatus disambiguationStatus = DisambiguationStatus.PENDING;

  /// 消歧方法（ROR_ID/RINGGOLD/GRID/NAME_MATCH/MANUAL）。
  @Enumerated(EnumType.STRING)
  @Column(name = "disambiguation_method", length = 50)
  private DisambiguationMethod disambiguationMethod;

  /// 消歧置信度（0.0000-1.0000）。
  @Column(name = "disambiguation_score", precision = 5, scale = 4)
  private BigDecimal disambiguationScore;

  /// 消歧时间（UTC）。
  @Column(name = "disambiguated_at")
  private Instant disambiguatedAt;

  // ========== 便捷方法 ==========

  /// 标记为已匹配。
  ///
  /// @param organizationId 关联机构 ID
  /// @param method 消歧方法
  /// @param score 置信度评分
  public void markAsMatched(Long organizationId, DisambiguationMethod method, BigDecimal score) {
    this.organizationId = organizationId;
    this.disambiguationStatus = DisambiguationStatus.MATCHED;
    this.disambiguationMethod = method;
    this.disambiguationScore = score;
    this.disambiguatedAt = Instant.now();
  }

  /// 标记为无法匹配。
  ///
  /// @param method 尝试的消歧方法
  public void markAsUnmatched(DisambiguationMethod method) {
    this.disambiguationStatus = DisambiguationStatus.UNMATCHED;
    this.disambiguationMethod = method;
    this.disambiguatedAt = Instant.now();
  }

  /// 标记为有歧义。
  ///
  /// @param method 尝试的消歧方法
  public void markAsAmbiguous(DisambiguationMethod method) {
    this.disambiguationStatus = DisambiguationStatus.AMBIGUOUS;
    this.disambiguationMethod = method;
    this.disambiguatedAt = Instant.now();
  }

  /// 判断是否有可用的机构标识符。
  ///
  /// @return true 如果有 ROR、Ringgold 或 GRID ID
  public boolean hasIdentifiers() {
    return (rorId != null && !rorId.isBlank())
        || (ringgoldId != null && !ringgoldId.isBlank())
        || (gridId != null && !gridId.isBlank());
  }

  /// 判断是否待消歧。
  ///
  /// @return true 如果状态为 PENDING
  public boolean isPending() {
    return disambiguationStatus == DisambiguationStatus.PENDING;
  }

  /// 判断是否已匹配。
  ///
  /// @return true 如果状态为 MATCHED
  public boolean isMatched() {
    return disambiguationStatus == DisambiguationStatus.MATCHED;
  }
}
