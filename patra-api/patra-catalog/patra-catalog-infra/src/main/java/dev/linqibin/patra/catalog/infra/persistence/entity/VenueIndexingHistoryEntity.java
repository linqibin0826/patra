package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.starter.jpa.entity.ChildJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/// 载体索引历史 JPA 实体，映射到表 `cat_venue_indexing_history`。
///
/// **表结构**：记录期刊在各索引数据库（MEDLINE、PMC 等）的索引历史，来源于 NLM Serfile。
///
/// **关键字段说明**：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `indexing_source` 索引来源：MEDLINE/PMC/PubMed Central 等
/// - `currently_indexed` 当前是否被索引
/// - `indexing_treatment` 索引处理方式：FULL（全文）/SELECTIVE（选择性）
/// - `citation_subset` 引用子集：IM/AIM/N/D 等
/// - `start_year`/`end_year` 索引起止年份
///
/// **索引说明**：
///
/// - 唯一索引 `uk_venue_indexing`: (venue_id, indexing_source, start_year) 防止重复
/// - 普通索引 `idx_venue_id`: venue_id 支持查询期刊的索引历史
/// - 复合索引 `idx_source_indexed`: (indexing_source, currently_indexed) 支持按来源筛选当前索引期刊
///
/// **使用场景**：
///
/// 1. 从 Serfile 导入期刊的 IndexingHistoryList 数据
/// 2. 追踪期刊的 MEDLINE 索引状态变化
/// 3. 筛选当前被 MEDLINE 索引的期刊
/// 4. 期刊收录历史分析
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue_indexing_history",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_indexing",
          columnNames = {"venue_id", "indexing_source", "start_year"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_source_indexed", columnList = "indexing_source, currently_indexed")
    })
public class VenueIndexingHistoryEntity extends ChildJpaEntity {

  /// 载体 ID（外键：cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 索引来源：MEDLINE/PMC 等
  @Column(name = "indexing_source", nullable = false, length = 50)
  private String indexingSource;

  /// 当前是否被索引（0=否，1=是）
  @Column(name = "currently_indexed")
  private Boolean currentlyIndexed;

  /// 索引处理方式：FULL/SELECTIVE
  @Column(name = "indexing_treatment", length = 20)
  private String indexingTreatment;

  /// 引用子集：IM/AIM/N/D/H/K/T/E/S/X/B/C/F/Q
  @Column(name = "citation_subset", length = 10)
  private String citationSubset;

  /// 索引开始年份
  @Column(name = "start_year")
  private Integer startYear;

  /// 索引开始卷号
  @Column(name = "start_volume", length = 20)
  private String startVolume;

  /// 索引开始期号
  @Column(name = "start_issue", length = 20)
  private String startIssue;

  /// 索引结束年份（仍在索引则为 null）
  @Column(name = "end_year")
  private Integer endYear;

  /// 索引结束卷号
  @Column(name = "end_volume", length = 20)
  private String endVolume;

  /// 索引结束期号
  @Column(name = "end_issue", length = 20)
  private String endIssue;
}
