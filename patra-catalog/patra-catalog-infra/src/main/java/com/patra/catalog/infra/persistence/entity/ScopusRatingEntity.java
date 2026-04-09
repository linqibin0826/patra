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

/// Scopus 期刊指标 JPA 实体，映射到表 `cat_venue_scopus_rating`。
///
/// **数据模型**：一个期刊每年一行，存储 CiteScore/SJR/SNIP 年度指标。
/// 历史年份可能仅有 `citeScore`，最新年份额外填充 SJR/SNIP/分区/百分位等详情。
///
/// **唯一约束**：`(venue_id, year)` — 每个期刊每年最多一条 Scopus 评级记录。
///
/// **数据来源**：通过 Scopus Serial Title API 获取，由 `ScopusDataMapper` 映射而来。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue_scopus_rating",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_year",
          columnNames = {"venue_id", "year"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_cite_score", columnList = "cite_score"),
      @Index(name = "idx_quartile", columnList = "quartile")
    })
public class ScopusRatingEntity extends ChildJpaEntity {

  /// 关联期刊 ID（逻辑外键 → cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 指标年份（2000-2100）
  @Column(name = "year", nullable = false)
  private Short year;

  /// CiteScore
  @Column(name = "cite_score", precision = 10, scale = 4)
  private BigDecimal citeScore;

  /// CiteScore Tracker（当年预估值）
  @Column(name = "cite_score_tracker", precision = 10, scale = 4)
  private BigDecimal citeScoreTracker;

  /// SCImago Journal Rank
  @Column(name = "sjr", precision = 10, scale = 4)
  private BigDecimal sjr;

  /// Source Normalized Impact per Paper
  @Column(name = "snip", precision = 10, scale = 4)
  private BigDecimal snip;

  /// 该年发文量
  @Column(name = "document_count")
  private Integer documentCount;

  /// 该年被引次数
  @Column(name = "citation_count")
  private Integer citationCount;

  /// 被引文献百分比
  @Column(name = "percent_cited", precision = 5, scale = 2)
  private BigDecimal percentCited;

  /// 主 ASJC 学科领域
  @Column(name = "subject_area", length = 200)
  private String subjectArea;

  /// CiteScore 分区（Q1-Q4）
  @Column(name = "quartile", length = 5)
  private String quartile;

  /// 学科内百分位排名
  @Column(name = "percentile", precision = 5, scale = 2)
  private BigDecimal percentile;

  /// Scopus Source ID
  @Column(name = "scopus_source_id", length = 20)
  private String scopusSourceId;

  /// 数据来源 URL
  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  /// 数据抓取时间（UTC，微秒精度）
  @Column(name = "fetched_at")
  private Instant fetchedAt;
}
