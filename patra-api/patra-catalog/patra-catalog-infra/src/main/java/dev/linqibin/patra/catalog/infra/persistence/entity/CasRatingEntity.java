package dev.linqibin.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ChildJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// CAS 中科院分区 JPA 实体，映射到表 `cat_venue_cas_rating`。
///
/// **数据模型**：支持同一期刊同一年度多个 CAS 版本共存（升级版/新锐版/基础版）。
///
/// **唯一约束**：`(venue_id, year, edition)` — 每个期刊每年每个版本最多一条记录。
///
/// **字段说明**：
///
/// - `edition` 存储 CAS 版本名称（如"升级版"、"新锐版"），与 `ChildJpaEntity.version`（乐观锁）无关
/// - `majorQuartile` 大类分区，`minorQuartile` 小类分区，两者独立评定
///
/// **数据来源**：通过 LetPub 爬取填充，由 `LetPubDataMapper` 从原始数据拆解而来。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue_cas_rating",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_year_edition",
          columnNames = {"venue_id", "year", "edition"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_major_quartile", columnList = "major_quartile"),
      @Index(name = "idx_is_top_journal", columnList = "is_top_journal")
    })
public class CasRatingEntity extends ChildJpaEntity {

  /// 关联期刊 ID（逻辑外键 → cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 分区年份（2000-2100）
  @Column(name = "year", nullable = false)
  private Short year;

  /// CAS 版本名称（升级版/新锐版/基础版）
  @Column(name = "edition", nullable = false, length = 20)
  private String edition;

  /// 大类学科（如"医学"）
  @Column(name = "major_category", length = 50)
  private String majorCategory;

  /// 大类分区（1区-4区）
  @Column(name = "major_quartile", length = 10)
  private String majorQuartile;

  /// 小类学科（如"肿瘤学"）
  @Column(name = "minor_subject", length = 100)
  private String minorSubject;

  /// 小类分区（1区-4区）
  @Column(name = "minor_quartile", length = 10)
  private String minorQuartile;

  /// 是否为 Top 期刊
  @Column(name = "is_top_journal")
  private Boolean isTopJournal;

  /// 是否为综述期刊
  @Column(name = "is_review_journal")
  private Boolean isReviewJournal;

  /// 数据来源 URL
  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  /// 数据抓取时间（UTC，微秒精度）
  @Column(name = "fetched_at")
  private Instant fetchedAt;
}
