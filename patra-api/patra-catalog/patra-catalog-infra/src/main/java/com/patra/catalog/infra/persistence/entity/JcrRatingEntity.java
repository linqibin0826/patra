package com.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ChildJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// JCR 期刊评级 JPA 实体，映射到表 `cat_venue_jcr_rating`。
///
/// **数据模型**：一个期刊每年一行，存储 Impact Factor 年度趋势数据。
/// 历史年份仅有 `impactFactor`，最新年份额外填充分区/排名/学科等详情。
///
/// **唯一约束**：`(venue_id, year)` — 每个期刊每年最多一条 JCR 评级记录。
///
/// **数据来源**：通过 LetPub 爬取填充，由 `LetPubDataMapper` 从原始数据拆解而来。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue_jcr_rating",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_year",
          columnNames = {"venue_id", "year"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_jif_quartile", columnList = "jif_quartile"),
      @Index(name = "idx_impact_factor", columnList = "impact_factor")
    })
public class JcrRatingEntity extends ChildJpaEntity {

  /// 关联期刊 ID（逻辑外键 → cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 评级年份（2000-2100）
  @Column(name = "year", nullable = false)
  private Short year;

  /// JIF 影响因子
  @Column(name = "impact_factor", precision = 10, scale = 4)
  private BigDecimal impactFactor;

  /// 五年影响因子（仅最新年）
  @Column(name = "five_year_if", precision = 10, scale = 4)
  private BigDecimal fiveYearIf;

  /// JCR 学科分类（如 "MULTIDISCIPLINARY SCIENCES"）
  @Column(name = "subject", length = 100)
  private String subject;

  /// JCR 收录集（SCIE/SSCI/AHCI）
  @Column(name = "collection", length = 10)
  private String collection;

  /// JIF 分区（Q1-Q4）
  @Column(name = "jif_quartile", length = 10)
  private String jifQuartile;

  /// JIF 排名（如 "1/100"）
  @Column(name = "jif_rank", length = 20)
  private String jifRank;

  /// JCI 分区
  @Column(name = "jci_quartile", length = 10)
  private String jciQuartile;

  /// JCI 排名
  @Column(name = "jci_rank", length = 20)
  private String jciRank;

  /// 研究方向/学科领域
  @Column(name = "research_direction", length = 200)
  private String researchDirection;

  /// 数据来源 URL
  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  /// 数据抓取时间（UTC，微秒精度）
  @Column(name = "fetched_at")
  private Instant fetchedAt;
}
