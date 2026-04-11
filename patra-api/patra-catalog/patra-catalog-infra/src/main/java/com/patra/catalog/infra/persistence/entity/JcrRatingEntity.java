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
/// **数据模型**：Clarivate JCR 按年发布的期刊评级时间序列（每期刊每年一行）。除 `impactFactor`
/// 之外的分区/排名/学科/百分位/JCI 独立字段/自引率/WOS 综合分区等，也都是 Clarivate 每版 JCR
/// 按年发布的年度指标——**语义上都是年度数据，非快照**。
///
/// **唯一约束**：`(venue_id, year)` — 每个期刊每年最多一条 JCR 评级记录。
///
/// **当前数据源局限**：目前仅通过 LetPub 爬取填充，而 LetPub 页面只展示最新年的详细分区
/// 数据（历史年仅有 `impactFactor` 趋势值）。故历史年行里 `impactFactor` 之外的字段目前
/// 均为 NULL——这是**数据源限制**而非 schema 错位。未来接入 Clarivate InCites API 或
/// 历史 JCR Excel 批量导入后，可回填这些年度指标。
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

  /// WOS 综合分区等级（1区-4区，LetPub 基于 JIF + JCI 综合评定）
  @Column(name = "wos_overall_quartile", length = 10)
  private String wosOverallQuartile;

  /// JIF 学科分类（如 "MULTIDISCIPLINARY SCIENCES"）
  @Column(name = "subject", length = 100)
  private String subject;

  /// JIF 收录集（SCIE/SSCI/AHCI）
  @Column(name = "collection", length = 10)
  private String collection;

  /// JIF 分区（Q1-Q4）
  @Column(name = "jif_quartile", length = 10)
  private String jifQuartile;

  /// JIF 排名（如 "2/136"）
  @Column(name = "jif_rank", length = 20)
  private String jifRank;

  /// JIF 学科百分位（如 "99%"）
  @Column(name = "jif_percentile", length = 10)
  private String jifPercentile;

  /// JCI 学科分类（多数情况下同 subject）
  @Column(name = "jci_subject", length = 100)
  private String jciSubject;

  /// JCI 收录集（多数情况下同 collection）
  @Column(name = "jci_collection", length = 10)
  private String jciCollection;

  /// JCI 分区
  @Column(name = "jci_quartile", length = 10)
  private String jciQuartile;

  /// JCI 排名
  @Column(name = "jci_rank", length = 20)
  private String jciRank;

  /// JCI 学科百分位（如 "98.9%"）
  @Column(name = "jci_percentile", length = 10)
  private String jciPercentile;

  /// JCI 数值（Journal Citation Indicator 本身的数值，如 11.14）
  @Column(name = "jci_value", precision = 10, scale = 4)
  private BigDecimal jciValue;

  /// 自引率（如 "1.6%"；Clarivate 年度指标，LetPub 仅提供最新年值）
  @Column(name = "self_citation_rate", length = 10)
  private String selfCitationRate;

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
